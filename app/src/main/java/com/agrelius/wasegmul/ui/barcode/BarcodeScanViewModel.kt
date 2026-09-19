package com.agrelius.wasegmul.ui.barcode

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.MLArbitrator
import com.agrelius.wasegmul.PackagingWasteMapper
import com.agrelius.wasegmul.PredictionCodec
import com.agrelius.wasegmul.ResolvedPackagingComponent
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.ml.BarcodeScanner
import com.agrelius.wasegmul.ml.ClassificationOutcome
import com.agrelius.wasegmul.ml.ModelManager
import com.agrelius.wasegmul.network.OffPackagingComponentDto
import com.agrelius.wasegmul.network.OffProductDto
import com.agrelius.wasegmul.notification.NotificationHelper
import com.agrelius.wasegmul.repository.BarcodeRepository
import com.agrelius.wasegmul.repository.BarcodeResolutionResult
import com.agrelius.wasegmul.repository.WasteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.agrelius.wasegmul.ml.classifiers.normalizeCategoryLabel
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
    val barcodeScanner: BarcodeScanner = BarcodeScanner(),
    private val settingsManager: com.agrelius.wasegmul.utils.SettingsManager? = null,
    private val context: Context? = null
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<BarcodeScanState>(BarcodeScanState.Scanning)
    val uiState: StateFlow<BarcodeScanState> = _uiState.asStateFlow()

    private val _navigateToResult = Channel<Long>(Channel.BUFFERED)
    val navigateToResult: Flow<Long> = _navigateToResult.receiveAsFlow()

    private val _lastXpGain = MutableStateFlow<com.agrelius.wasegmul.gamification.XpGainResult?>(null)
    val lastXpGain: StateFlow<com.agrelius.wasegmul.gamification.XpGainResult?> = _lastXpGain.asStateFlow()

    // Last awarded scan for XP dedup (spam-scanning the same item earns 0 XP).
    private var lastAwardedSubclass: String? = null
    private var lastAwardedAtMs: Long? = null

    // In-flight Tier-1/3 resolve: cancelled by resumeScanning (Cancel /
    // scan-another) so a stale 20 s OFF cascade cannot resurrect a dead state
    // — or crash the process with an uncaught throw after the screen moved on.
    private var resolveJob: Job? = null

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
            recycleFrameBitmap(frameBitmap)
            return
        }

        val trimmed = barcode.trim()
        if (!isValidBarcode(trimmed)) {
            recycleFrameBitmap(frameBitmap)
            return
        }

        _uiState.value = BarcodeScanState.Resolving(trimmed)
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            try {
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
                if (frameBitmap != null && !frameBitmap.isRecycled && modelManager != null) {
                    try {
                        modelManager.ensureInitialized()
                        val outcome = modelManager.classify(frameBitmap)
                    if (outcome is ClassificationOutcome.Success) {
                        val arbitrated = MLArbitrator.arbitrate(outcome.prediction)
                        val category = normalizeCategoryLabel(arbitrated.category)
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
                        // Do NOT persist Tier-4 visual guesses to the product cache:
                        // an unverified ML guess must never become ground truth for
                        // future scans. It lives in-memory only (this Resolved state).
                        _uiState.value = BarcodeScanState.Resolved(
                            product = product,
                            components = listOf(primaryComponent),
                            source = "visual_ml"
                        )
                        return@launch
                    }
                } catch (e: CancellationException) {
                    // Cancel (scan-another / back) must stay silent: never let a
                    // torn-down Tier-4 overwrite the fresh Scanning state.
                    throw e
                } catch (e: OutOfMemoryError) {
                    android.util.Log.w("BarcodeScanViewModel", "Visual ML fallback OOM; frame dropped")
                } catch (e: Exception) {
                    android.util.Log.w("BarcodeScanViewModel", "Visual ML fallback failed: ${e.message}")
                }
            }

            if (result is BarcodeResolutionResult.Error) {
                _uiState.value = BarcodeScanState.Error(result.message)
            } else {
                _uiState.value = BarcodeScanState.NotFound(trimmed)
            }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("BarcodeScanViewModel", "Barcode resolve ran out of memory", e)
                _uiState.value = BarcodeScanState.Error(
                    "That frame was too large to process. Please try again."
                )
            } catch (e: Exception) {
                // An uncaught throw here used to escape viewModelScope and crash
                // the process ("app keeps stopping"). Surface it instead.
                android.util.Log.e("BarcodeScanViewModel", "Barcode resolve failed", e)
                _uiState.value = BarcodeScanState.Error(
                    "Couldn't look up that code. Please try again."
                )
            } finally {
                // Tier-4 ownership: the frame bitmap is consumed here and never
                // retained by Resolved state — recycle it so repeat scans cannot
                // accumulate full-res frames into an OOM kill.
                recycleFrameBitmap(frameBitmap)
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
        val category = normalizeCategoryLabel(arbitrated.category)

        // Weight chain (single source): measured product grams -> canonical
        // per-subclass estimate (WasteMapping) -> 0.0 (unknown, never fabricated).
        // Zero-weight records earn no Eco credit and no celebration (same policy
        // as uncertain camera scans).
        val estimatedWeightKg = (product.weightGrams
            ?: (WasteMapping.getWeight(arbitrated.subcategory) * 1000.0)) / 1000.0
        val name = product.productName?.trim()
        val brand = product.brand?.trim()
        // Legacy compat tag (kept for old history rows); new rows use first-class columns.
        val vectorTag = buildString {
            append("barcode:${product.barcode}")
            if (!name.isNullOrBlank()) append("|$name")
            if (!brand.isNullOrBlank()) append("|$brand")
        }

        val record = WasteRecord(
            category = category,
            subclass = arbitrated.subcategory,
            confidence = arbitrated.categoryConfidence,
            estimatedWeight = estimatedWeightKg,
            featureVector = vectorTag,
            topPredictions = PredictionCodec.encode(arbitrated.topSubcategories),
            timestamp = System.currentTimeMillis(),
            source = "barcode",
            productName = name?.takeIf { it.isNotBlank() },
            barcode = product.barcode
        )
        // Room I/O off-Main (ANR guard); unbounded throws would otherwise wedge
        // the Save button with no error UI.
        val id = withContext(Dispatchers.IO) { wasteRepository.insert(record) }
        // Parity with camera pipeline: barcode scans earn XP + daily streak.
        val now = System.currentTimeMillis()
        try {
            val sm = settingsManager
            if (sm != null) {
                val impact = com.agrelius.wasegmul.EcoImpactCalculator.calculate(listOf(record))
                val co2Grams = impact.co2PreventedKg * 1000.0
                val dailyCount = withContext(Dispatchers.IO) { sm.incrementDailyScanCount() }
                val currentXp = withContext(Dispatchers.IO) { sm.totalXp.first() }
                val xpResult = com.agrelius.wasegmul.gamification.GamificationManager.processNewScan(
                    currentTotalXp = currentXp,
                    category = category,
                    confidence = arbitrated.categoryConfidence,
                    co2PreventedGrams = co2Grams,
                    dailyScanCount = dailyCount,
                    subclass = arbitrated.subcategory,
                    scanTimestampMs = now,
                    lastSubclass = lastAwardedSubclass,
                    lastScanTimestampMs = lastAwardedAtMs
                )
                lastAwardedSubclass = arbitrated.subcategory
                lastAwardedAtMs = now
                withContext(Dispatchers.IO) { sm.setTotalXp(xpResult.newTotalXp) }
                _lastXpGain.value = xpResult
                if (xpResult.didLevelUp) {
                    context?.let { ctx ->
                        NotificationHelper.showLevelUpNotification(
                            ctx,
                            xpResult.newLevel,
                            xpResult.xpEarned
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BarcodeScanViewModel", "XP award failed", e)
        }
        // trySend (BUFFERED channel): never suspends if the screen already popped.
        _navigateToResult.trySend(id)
        return id
    }

    /**
     * Quickly classifies an unindexed barcode or QR code into a chosen waste category/subclass,
     * caches it in Room for future scans, saves a WasteRecord to history, and navigates to the Result screen.
     *
     * @param barcode The scanned barcode or QR code string.
     * @param productName User-provided or inferred product name (e.g. "Uno Card Box", "Laptop").
     * @param category WasegMul waste category (e.g. "Recyclable", "E-Waste", "Residual", "Organic").
     * @param subclass WasegMul waste subclass (e.g. "Cardboard", "Electronics", "Plastic", "Metal").
     * @param persistCache False for pseudo-codes (e.g. error-state "code_..." stand-ins)
     * that must never pollute the barcode cache as junk keys.
     */
    fun quickClassifyAndSave(
        barcode: String,
        productName: String,
        category: String,
        subclass: String,
        persistCache: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val name = productName.ifBlank { "Scanned Item ($barcode)" }
            // Canonical per-subclass estimate (WasteMapping, grams) — never the old
            // coarse buckets (E-Waste 1500 g / Metal 250 g / else 50 g) that
            // fabricated CO2/water/energy figures. confirmAndSave derives Eco/XP
            // from the same source, so history stays consistent.
            val normalizedCategory = normalizeCategoryLabel(category)
            val weight = WasteMapping.getWeight(subclass) * 1000.0
            val isPseudoCode = barcode.startsWith("code_")
            val product = BarcodeProduct(
                barcode = barcode,
                productName = name,
                brand = null,
                category = normalizedCategory,
                subclass = subclass,
                materials = subclass,
                componentsJson = null,
                weightGrams = weight,
                ecoscore = null,
                source = "user_identified",
                packagingsComplete = true
            )
            if (persistCache && !isPseudoCode) {
                try {
                    withContext(Dispatchers.IO) {
                        barcodeRepository.saveUserIdentifiedProduct(product)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Non-fatal if cache write fails
                }
            }
            confirmAndSave(product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("BarcodeScanViewModel", "quickClassify ran out of memory", e)
                _uiState.value = BarcodeScanState.Error(
                    "Couldn't save that item: out of memory. Please try again."
                )
            } catch (e: Exception) {
                // An uncaught throw here used to escape viewModelScope and crash
                // the process on Confirm. Surface it instead.
                android.util.Log.e("BarcodeScanViewModel", "quickClassifyAndSave failed", e)
                _uiState.value = BarcodeScanState.Error(
                    "Couldn't save that item. Please try again."
                )
            }
        }
    }

    /**
     * Convenience method to trigger [confirmAndSave] asynchronously within [viewModelScope].
     *
     * @param product The resolved barcode product to persist.
     */
    fun saveAndNavigate(product: BarcodeProduct) {
        viewModelScope.launch {
            try {
                confirmAndSave(product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("BarcodeScanViewModel", "saveAndNavigate ran out of memory", e)
                _uiState.value = BarcodeScanState.Error(
                    "Couldn't save that item: out of memory. Please try again."
                )
            } catch (e: Exception) {
                android.util.Log.e("BarcodeScanViewModel", "saveAndNavigate failed", e)
                _uiState.value = BarcodeScanState.Error(
                    "Couldn't save that item. Please try again."
                )
            }
        }
    }

    /**
     * Resets the UI state back to active scanning and clears the scanner debounce cooldown.
     */
    fun resumeScanning() {
        // Cancel the in-flight resolve first: without this a stale OFF cascade
        // overwrites the fresh Scanning state (or throws after the screen moved on).
        resolveJob?.cancel()
        resolveJob = null
        _uiState.value = BarcodeScanState.Scanning
        barcodeScanner.resetCooldown()
    }

    /**
     * Best-effort recycle of a Tier-4 frame bitmap (ownership transfers from the
     * scanner on every detection). Never throws: called on early-return paths.
     */
    private fun recycleFrameBitmap(bitmap: Bitmap?) {
        // No-op: let Android GC safely reclaim memory without native race conditions
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
        private val barcodeScanner: BarcodeScanner? = null,
        private val settingsManager: com.agrelius.wasegmul.utils.SettingsManager? = null,
        private val context: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BarcodeScanViewModel::class.java)) {
                return BarcodeScanViewModel(
                    barcodeRepository = barcodeRepository,
                    wasteRepository = wasteRepository,
                    modelManager = modelManager,
                    barcodeScanner = barcodeScanner ?: BarcodeScanner(),
                    settingsManager = settingsManager,
                    context = context
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
