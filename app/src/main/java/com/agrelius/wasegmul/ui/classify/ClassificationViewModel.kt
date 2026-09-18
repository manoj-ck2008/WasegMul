package com.agrelius.wasegmul.ui.classify

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.ClassificationResult
import com.agrelius.wasegmul.MLArbitrator
import com.agrelius.wasegmul.ml.ClassificationOutcome
import com.agrelius.wasegmul.ml.FailureReason
import com.agrelius.wasegmul.ml.ModelInitException
import com.agrelius.wasegmul.PredictionCodec
import com.agrelius.wasegmul.WasteKnowledgeBase
import com.agrelius.wasegmul.WasteMapping
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.ml.ModelManager
import com.agrelius.wasegmul.ml.classifiers.normalizeCategoryLabel
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.SettingsManager
import com.agrelius.wasegmul.gamification.GamificationManager
import com.agrelius.wasegmul.gamification.XpGainResult
import com.agrelius.wasegmul.EcoImpactCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ClassificationViewModel(
    private val repository: WasteRepository,
    private val settingsManager: SettingsManager,
    // Shared app-scoped ModelManager (WasegMulApp.modelManager). Must be
    // injected so camera + barcode + YOLO do not each hold a TFLite residency
    // (duplicate residency = OOM). Only closed here when we created it.
    private var modelManager: ModelManager? = null
) : ViewModel() {

    private var ownsModelManager = false
    private var classifyJob: Job? = null
    // Serializes the read-modify-write XP sequence (daily count + total XP).
    private val xpMutex = Mutex()
    // Last awarded scan for XP dedup (spam-scanning the same item earns 0 XP).
    private var lastAwardedSubclass: String? = null
    private var lastAwardedAtMs: Long? = null

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap

    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult

    private val _lastXpGain = MutableStateFlow<XpGainResult?>(null)
    val lastXpGain: StateFlow<XpGainResult?> = _lastXpGain

    private val _isFreshScan = MutableStateFlow(false)
    val isFreshScan: StateFlow<Boolean> = _isFreshScan

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Model warm-up failures surface here so ClassifyScreen can render a
    // dedicated error UI instead of failing only on Classify press.
    private val _modelInitError = MutableStateFlow<String?>(null)
    val modelInitError: StateFlow<String?> = _modelInitError

    private val _currentRecord = MutableStateFlow<WasteRecord?>(null)
    val currentRecord: StateFlow<WasteRecord?> = _currentRecord

    private val _navigateToResult = Channel<Long>(Channel.CONFLATED)
    val navigateToResult = _navigateToResult.receiveAsFlow()

    fun initModel(context: Context, shared: ModelManager? = null) {
        if (modelManager == null) {
            if (shared != null) {
                modelManager = shared
                ownsModelManager = false
            } else {
                modelManager = ModelManager(context.applicationContext)
                ownsModelManager = true
            }
        }
        // Pre-warm off-Main; surface failures for the model-init error UI.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager?.ensureInitialized()
                _modelInitError.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Model pre-warm failed", e)
                _modelInitError.value =
                    "The AI models failed to load. Check storage space and retry."
            }
        }
    }

    fun retryInitModel() {
        _modelInitError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager?.ensureInitialized()
                _modelInitError.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Model retry failed", e)
                _modelInitError.value =
                    "The AI models failed to load. Check storage space and retry."
            }
        }
    }

    fun setBitmap(bitmap: Bitmap) {
        // Do NOT manually recycle: Compose may still be drawing the old bitmap
        // during navigation transitions (native crash). Just drop the reference
        // and let GC reclaim. Callers pass a fresh bitmap each time.
        // A new image also invalidates any in-flight inference on the old one.
        classifyJob?.cancel()
        classifyJob = null
        _isLoading.value = false
        _capturedBitmap.value = bitmap
        _classificationResult.value = null
        _error.value = null
        _currentRecord.value = null
    }

    fun classify() {
        val bitmap = _capturedBitmap.value ?: run {
            _error.value = "No image selected. Please capture or pick a photo first."
            return
        }
        if (bitmap.isRecycled) {
            _error.value = "Image was released. Please reselect the photo."
            return
        }
        if (_isLoading.value) return
        classifyJob?.cancel()
        classifyJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Ensure models are loaded before inference (IO: mmap/init).
                withContext(Dispatchers.IO) { modelManager?.ensureInitialized() }

                val outcome = withContext(Dispatchers.Default) {
                    modelManager?.classify(bitmap)
                } ?: run {
                    _error.value = "Models not initialised. Please restart the app."
                    _isLoading.value = false
                    return@launch
                }

                when (outcome) {
                    is ClassificationOutcome.Success -> {
                        val prediction = outcome.prediction
                        // Honor user-tuned confidence threshold from Settings (IO).
                        val userThreshold = try {
                            withContext(Dispatchers.IO) {
                                settingsManager.confidenceThreshold.first()
                            }
                        } catch (e: Exception) {
                            MLArbitrator.LOW_CONFIDENCE_THRESHOLD
                        }
                        val finalPrediction = MLArbitrator.arbitrate(prediction, userThreshold)
                        // Normalize model labels to CONTEXT.md vocabulary (Trash -> Residual)
                        // before KB lookup, persistence, and Eco/XP crediting.
                        val normalizedCategory = normalizeCategoryLabel(finalPrediction.category)
                        val info = withContext(Dispatchers.IO) {
                            WasteKnowledgeBase.getInfo(
                                normalizedCategory,
                                finalPrediction.subcategory
                            )
                        }
                        val isUncertain =
                            normalizedCategory == WasteMapping.UNCERTAIN ||
                                normalizedCategory == WasteMapping.UNKNOWN

                        val result = ClassificationResult(
                            category = normalizedCategory,
                            subclass = finalPrediction.subcategory,
                            confidence = if (isUncertain) finalPrediction.categoryConfidence
                            else finalPrediction.subcategoryConfidence,
                            topPredictions = finalPrediction.topSubcategories,
                            disposalGuide = info.disposalGuide,
                            environmentalImpact = info.environmentalImpact,
                            recyclingBenefits = info.recyclingBenefits,
                            sources = info.sources,
                            classificationMessage = finalPrediction.classificationMessage
                        )

                        _classificationResult.value = result

                        val estimatedWeight = if (isUncertain) 0.0
                        else WasteMapping.getWeight(result.subclass)

                        val now = System.currentTimeMillis()
                        val record = WasteRecord(
                            category = result.category,
                            subclass = result.subclass,
                            confidence = result.confidence,
                            estimatedWeight = estimatedWeight,
                            featureVector = null,
                            topPredictions = PredictionCodec.encode(result.topPredictions),
                            timestamp = now
                        )
                        // Insert FIRST, then navigate: ResultScreen needs the row ID
                        // for feedback/corrections. Navigating before insert loses data.
                        // Room I/O off-Main.
                        val id = withContext(Dispatchers.IO) { repository.insert(record) }
                        _currentRecord.value = record.copy(id = id)

                        // Gamification: calculate XP earned from this scan.
                        // Serialized: daily-count + total-XP is read-modify-write.
                        try {
                            val impact = withContext(Dispatchers.Default) {
                                EcoImpactCalculator.calculate(listOf(record))
                            }
                            val co2Grams = impact.co2PreventedKg * 1000.0
                            xpMutex.withLock {
                                val dailyCount = withContext(Dispatchers.IO) {
                                    settingsManager.incrementDailyScanCount()
                                }
                                val currentXp = withContext(Dispatchers.IO) {
                                    settingsManager.totalXp.first()
                                }
                                val xpResult = GamificationManager.processNewScan(
                                    currentTotalXp = currentXp,
                                    category = result.category,
                                    confidence = result.confidence,
                                    co2PreventedGrams = co2Grams,
                                    dailyScanCount = dailyCount,
                                    subclass = result.subclass,
                                    scanTimestampMs = now,
                                    lastSubclass = lastAwardedSubclass,
                                    lastScanTimestampMs = lastAwardedAtMs
                                )
                                lastAwardedSubclass = result.subclass
                                lastAwardedAtMs = now
                                withContext(Dispatchers.IO) {
                                    settingsManager.setTotalXp(xpResult.newTotalXp)
                                }
                                _lastXpGain.value = xpResult
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "XP calculation failed", e)
                            // No fake fallback: a failed XP write surfaces as no
                            // celebration (consume-once flags stay null/false).
                            _lastXpGain.value = null
                        }

                        // Uncertain / zero-impact scans never celebrate: the
                        // overlay would otherwise reward an unidentified item.
                        _isFreshScan.value = !isUncertain && estimatedWeight > 0.0
                        if (!_isFreshScan.value) {
                            _lastXpGain.value = null
                        }
                        _navigateToResult.trySend(id)
                    }
                    is ClassificationOutcome.Failure -> {
                        val msg = when (outcome.reason) {
                            FailureReason.MODEL_LOAD_FAILED ->
                                "Unable to load the AI models. Please restart the app or check available storage."
                            FailureReason.CATEGORY_INFERENCE_FAILED ->
                                "The category model failed to process the image. Please try again with a clearer photo."
                            FailureReason.SUBCLASS_INFERENCE_FAILED ->
                                "The subclass model failed to process the image. Please try again with a clearer photo."
                            FailureReason.NOT_INITIALIZED ->
                                "Models have not been initialised. Please restart the app."
                            FailureReason.UNKNOWN ->
                                outcome.message.ifBlank { "An unexpected error occurred. Please try again." }
                        }
                        Log.e(TAG, "Classification failed: ${outcome.reason}: $msg", outcome.cause)
                        _error.value = msg
                    }
                }
            } catch (e: ModelInitException) {
                Log.e(TAG, "Model initialisation failed", e)
                _error.value = "Unable to load the AI models. Restart the app or check available storage."
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during classification", e)
                _error.value = "An unexpected error occurred. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Cancels an in-flight classification (Cancel button / system back). */
    fun cancelClassification() {
        classifyJob?.cancel()
        classifyJob = null
        _isLoading.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun releaseBitmap() {
        // Drop reference only; do not recycle (Compose may still hold it).
        cancelClassification()
        _capturedBitmap.value = null
    }

    fun setFeedback(feedback: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = repository.updateFeedback(recordId, feedback)
                if (rows > 0) {
                    _currentRecord.value = _currentRecord.value?.copy(feedback = feedback)
                } else {
                    _error.value = "Record not found: feedback not saved."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update feedback", e)
                _error.value = "Failed to save feedback."
            }
        }
    }

    fun setCorrection(correctedSubclass: String) {
        // Taxonomy guard: Categories must never land in correctedSubclass.
        if (com.agrelius.wasegmul.ui.result.isCategoryName(correctedSubclass)) {
            _error.value = "Please choose a specific material (Subclass), not a Category."
            return
        }
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = repository.updateCorrectionWithCategory(recordId, correctedSubclass)
                if (rows > 0) {
                    val correctedCategory = WasteMapping.getCategory(correctedSubclass)
                    _currentRecord.value = _currentRecord.value?.copy(
                        correctedSubclass = correctedSubclass,
                        category = if (correctedCategory != WasteMapping.UNKNOWN) correctedCategory
                                   else _currentRecord.value?.category ?: ""
                    )
                } else {
                    _error.value = "Record not found: correction not saved."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update correction", e)
                _error.value = "Failed to save correction."
            }
        }
    }

    fun loadRecord(recordId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _classificationResult.value = null
            _currentRecord.value = null
            // Drop any large camera bitmap while viewing history to halve memory.
            _capturedBitmap.value = null
            _isFreshScan.value = false
            _lastXpGain.value = null
            try {
                val record = repository.getRecordById(recordId)
                if (record == null) {
                    _error.value = "Record not found. It may have been deleted."
                    return@launch
                }
                val info = WasteKnowledgeBase.getInfo(record.category, record.subclass)
                // Single-parser parity with Result/Home/History: first-class
                // source/barcode/productName columns win, legacy vector fallback.
                val display = com.agrelius.wasegmul.ui.result.parseBarcodeDisplay(
                    record.source, record.barcode, record.productName, record.featureVector
                )
                val classificationMsg = if (display != null) {
                    val prodName = display.productName
                    if (prodName != null) {
                        "Product verified via barcode: $prodName (${display.code}). Packaging classification: ${record.subclass} (${record.category})."
                    } else {
                        "Product verified via barcode (${display.code}). Ground-truth packaging classification: ${record.subclass} (${record.category})."
                    }
                } else {
                    "Historical record: originally classified as ${record.subclass} (${record.category})."
                }
                _classificationResult.value = ClassificationResult(
                    category = record.category,
                    subclass = record.subclass,
                    confidence = record.confidence,
                    topPredictions = PredictionCodec.decode(record.topPredictions),
                    disposalGuide = info.disposalGuide,
                    environmentalImpact = info.environmentalImpact,
                    recyclingBenefits = info.recyclingBenefits,
                    sources = info.sources,
                    classificationMessage = classificationMsg
                )
                _currentRecord.value = record
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load record $recordId", e)
                _error.value = "Failed to load historical record."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Only close a manager we created; the app singleton outlives us.
        if (ownsModelManager) {
            modelManager?.close()
            modelManager = null
        }
        classifyJob?.cancel()
        _capturedBitmap.value = null
    }

    fun consumeXpGain() {
        _lastXpGain.value = null
    }

    /** Consume-once: overlays must call this on dismiss so rotation cannot replay them. */
    fun consumeFreshScan() {
        _isFreshScan.value = false
    }

    class Factory(
        private val repository: WasteRepository,
        private val settingsManager: SettingsManager,
        private val modelManager: ModelManager? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClassificationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ClassificationViewModel(repository, settingsManager, modelManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "ClassificationVM"
    }
}
