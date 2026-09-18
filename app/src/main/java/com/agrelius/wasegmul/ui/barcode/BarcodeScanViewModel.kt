package com.agrelius.wasegmul.ui.barcode

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.MLArbitrator
import com.agrelius.wasegmul.PackagingWasteMapper
import com.agrelius.wasegmul.ResolvedPackagingComponent
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.ml.BarcodeScanner
import com.agrelius.wasegmul.ml.ClassificationOutcome
import com.agrelius.wasegmul.ml.ModelManager
import com.agrelius.wasegmul.network.OffPackagingComponentDto
import com.agrelius.wasegmul.network.OffProductDto
import com.agrelius.wasegmul.repository.BarcodeRepository
import com.agrelius.wasegmul.repository.BarcodeResolutionResult
import com.agrelius.wasegmul.repository.WasteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * UI state representation for the barcode scanning workflow.
 */
sealed interface BarcodeScanState {
    /** Actively capturing camera frames and searching for barcode symbols. */
    data object Scanning : BarcodeScanState

    /** Resolving the detected barcode against local database and remote API. */
    data class Resolving(val barcode: String) : BarcodeScanState

    /**
     * Successfully resolved product metadata and packaging components.
     *
     * @property product The resolved barcode product record.
     * @property components Decomposed packaging components with domain disposal rules.
     * @property source Data source provenance ("preloaded", "cached", "api", or "visual_ml").
     */
    data class Resolved(
        val product: BarcodeProduct,
        val components: List<ResolvedPackagingComponent>,
        val source: String
    ) : BarcodeScanState

    /** Barcode was detected but could not be located in local or remote databases. */
    data class NotFound(val barcode: String) : BarcodeScanState

    /** An error occurred during barcode resolution or network query. */
    data class Error(val message: String) : BarcodeScanState
}

/**
 * ViewModel managing the barcode scanner screen state and resolution pipeline.
 *
 * Coordinates between camera-driven barcode detection, the 4-tier barcode repository
 * pipeline, packaging component decomposition, and persistence into the user's waste history.
 *
 * @property barcodeRepository Repository resolving barcodes against local DB and Open Food Facts API.
 * @property wasteRepository Repository storing canonical classification records.
 * @property modelManager TFLite ModelManager running Tier 4 visual ML fallback when barcode is unindexed.
 * @property barcodeScanner Camera frame ML Kit barcode scanner instance.
 */
class BarcodeScanViewModel(
    private val barcodeRepository: BarcodeRepository,
    private val wasteRepository: WasteRepository,
    private val modelManager: ModelManager? = null,
    val barcodeScanner: BarcodeScanner = BarcodeScanner()
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<BarcodeScanState>(BarcodeScanState.Scanning)
    val uiState: StateFlow<BarcodeScanState> = _uiState.asStateFlow()

    private val _navigateToResult = Channel<Long>(Channel.BUFFERED)
    val navigateToResult: Flow<Long> = _navigateToResult.receiveAsFlow()

    /**
     * Handles detection of a barcode symbol from the camera frame analysis pipeline.
     *
     * Validates barcode format, transitions state to [BarcodeScanState.Resolving],
     * and triggers resolution through the 4-tier pipeline (DB -> Cache -> OFF API -> Visual ML).
     *
     * @param barcode The raw barcode string emitted by the scanner.
     * @param frameBitmap Optional camera frame captured simultaneously for Tier 4 Visual ML fallback.
     */
    fun onBarcodeDetected(barcode: String, frameBitmap: Bitmap? = null) {
        if (_uiState.value !is BarcodeScanState.Scanning) {
            return
        }

        val trimmed = barcode.trim()
        if (!isValidBarcode(trimmed)) {
            return
        }

        _uiState.value = BarcodeScanState.Resolving(trimmed)
        viewModelScope.launch {
            val result = barcodeRepository.resolve(trimmed)
            if (result is BarcodeResolutionResult.Found) {
                val components = parseComponents(result.product)
                _uiState.value = BarcodeScanState.Resolved(
                    product = result.product,
                    components = components,
                    source = result.source
                )
                return@launch
            }

            // Tier 4: Automatic Visual ML Fallback using camera frame
            if (frameBitmap != null && modelManager != null) {
                try {
                    modelManager.ensureInitialized()
                    val outcome = modelManager.classify(frameBitmap)
                    if (outcome is ClassificationOutcome.Success) {
                        val arbitrated = MLArbitrator.arbitrate(outcome.prediction)
                        val category = arbitrated.category
                        val subclass = arbitrated.subcategory
                        val weight = WasteMapping.getWeight(subclass) * 1000.0
                        val primaryComponent = ResolvedPackagingComponent(
                            shape = "Item",
                            material = subclass,
                            category = category,
                            subclass = subclass,
                            disposalAction = PackagingWasteMapper.mapDisposalAction(null, category),
                            weightGrams = weight
                        )
                        val product = BarcodeProduct(
                            barcode = trimmed,
                            productName = "Identified Item ($subclass)",
                            brand = null,
                            category = category,
                            subclass = subclass,
                            materials = subclass,
                            componentsJson = null,
                            weightGrams = weight,
                            ecoscore = null,
                            source = "visual_ml",
                            packagingsComplete = true
                        )
                        barcodeRepository.saveUserIdentifiedProduct(product)
                        _uiState.value = BarcodeScanState.Resolved(
                            product = product,
                            components = listOf(primaryComponent),
                            source = "visual_ml"
                        )
                        return@launch
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BarcodeScanViewModel", "Visual ML fallback failed: ${e.message}")
                }
            }

            if (result is BarcodeResolutionResult.Error) {
                _uiState.value = BarcodeScanState.Error(result.message)
            } else {
                _uiState.value = BarcodeScanState.NotFound(trimmed)
            }
        }
    }

    /**
     * Confirms the resolved product classification, builds a canonical [WasteRecord],
     * persists it to the [wasteRepository], and triggers navigation to the Result screen.
     *
     * @param product The resolved barcode product to persist.
     * @return The auto-generated database row ID of the newly created waste record.
     */
    suspend fun confirmAndSave(product: BarcodeProduct): Long {
        val evidence = com.agrelius.wasegmul.BarcodeEvidence(
            productName = product.productName ?: "Product",
            category = product.category,
            subclass = product.subclass,
            isComplete = product.packagingsComplete
        )
        val arbitrated = com.agrelius.wasegmul.MLArbitrator.arbitrateWithBarcode(null, evidence)

        val estimatedWeightKg = (product.weightGrams ?: 50.0) / 1000.0
        val name = product.productName?.trim()
        val brand = product.brand?.trim()
        val vectorTag = buildString {
            append("barcode:${product.barcode}")
            if (!name.isNullOrBlank()) append("|$name")
            if (!brand.isNullOrBlank()) append("|$brand")
        }

        val record = WasteRecord(
            category = arbitrated.category,
            subclass = arbitrated.subcategory,
            confidence = arbitrated.categoryConfidence,
            estimatedWeight = estimatedWeightKg,
            featureVector = vectorTag,
            topPredictions = arbitrated.topSubcategories.joinToString(",") { "${it.first}|${it.second}" },
            timestamp = System.currentTimeMillis()
        )
        val id = wasteRepository.insert(record)
        _navigateToResult.send(id)
        return id
    }

    /**
     * Quickly classifies an unindexed barcode or QR code into a chosen waste category/subclass,
     * caches it in Room for future scans, saves a WasteRecord to history, and navigates to the Result screen.
     *
     * @param barcode The scanned barcode or QR code string.
     * @param productName User-provided or inferred product name (e.g. "Uno Card Box", "Laptop").
     * @param category WasegMul waste category (e.g. "Recyclable", "E-Waste", "Trash", "Organic").
     * @param subclass WasegMul waste subclass (e.g. "Cardboard", "Electronics", "Plastic", "Metal").
     */
    fun quickClassifyAndSave(
        barcode: String,
        productName: String,
        category: String,
        subclass: String
    ) {
        viewModelScope.launch {
            val name = productName.ifBlank { "Scanned Item ($barcode)" }
            val weight = when (category) {
                "E-Waste" -> 1500.0
                "Recyclable" -> if (subclass == "Metal" || subclass == "Glass") 250.0 else 50.0
                else -> 50.0
            }
            val product = BarcodeProduct(
                barcode = barcode,
                productName = name,
                brand = null,
                category = category,
                subclass = subclass,
                materials = subclass,
                componentsJson = null,
                weightGrams = weight,
                ecoscore = null,
                source = "user_identified",
                packagingsComplete = true
            )
            try {
                barcodeRepository.saveUserIdentifiedProduct(product)
            } catch (e: Exception) {
                // Non-fatal if cache write fails
            }
            confirmAndSave(product)
        }
    }

    /**
     * Convenience method to trigger [confirmAndSave] asynchronously within [viewModelScope].
     *
     * @param product The resolved barcode product to persist.
     */
    fun saveAndNavigate(product: BarcodeProduct) {
        viewModelScope.launch {
            confirmAndSave(product)
        }
    }

    /**
     * Resets the UI state back to active scanning and clears the scanner debounce cooldown.
     */
    fun resumeScanning() {
        _uiState.value = BarcodeScanState.Scanning
        barcodeScanner.resetCooldown()
    }

    /**
     * Validates barcode format for any non-blank barcode, QR code, or serial symbol.
     *
     * @param code Barcode string to validate.
     * @return True if barcode matches non-blank string within valid length limits.
     */
    private fun isValidBarcode(code: String): Boolean {
        val trimmed = code.trim()
        return trimmed.length in 2..512
    }

    /**
     * Parses structured packaging components from JSON or creates a fallback component.
     *
     * @param product The barcode product with potential components JSON.
     * @return List of resolved packaging components with category and disposal action.
     */
    private fun parseComponents(product: BarcodeProduct): List<ResolvedPackagingComponent> {
        val jsonStr = product.componentsJson
        if (!jsonStr.isNullOrBlank()) {
            try {
                val dtos = json.decodeFromString<List<OffPackagingComponentDto>>(jsonStr)
                if (dtos.isNotEmpty()) {
                    val resolved = PackagingWasteMapper.resolveComponents(OffProductDto(packagings = dtos))
                    if (resolved.isNotEmpty()) {
                        return resolved
                    }
                }
            } catch (e: Exception) {
                try {
                    val components = json.decodeFromString<List<ResolvedPackagingComponent>>(jsonStr)
                    if (components.isNotEmpty()) {
                        return components
                    }
                } catch (ignored: Exception) {
                    // Fallback to single component
                }
            }
        }

        val fallbackMaterial = product.materials?.ifBlank { null } ?: product.subclass
        val fallbackShape = "Packaging"
        val disposalAction = PackagingWasteMapper.mapDisposalAction(null, product.category)

        return listOf(
            ResolvedPackagingComponent(
                shape = fallbackShape,
                material = fallbackMaterial,
                category = product.category,
                subclass = product.subclass,
                disposalAction = disposalAction,
                weightGrams = product.weightGrams
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        barcodeScanner.close()
    }

    /**
     * Factory for constructing [BarcodeScanViewModel] instances with injected repositories.
     */
    class Factory(
        private val barcodeRepository: BarcodeRepository,
        private val wasteRepository: WasteRepository,
        private val modelManager: ModelManager? = null,
        private val barcodeScanner: BarcodeScanner? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BarcodeScanViewModel::class.java)) {
                return BarcodeScanViewModel(
                    barcodeRepository = barcodeRepository,
                    wasteRepository = wasteRepository,
                    modelManager = modelManager,
                    barcodeScanner = barcodeScanner ?: BarcodeScanner()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
