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
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.SettingsManager
import com.agrelius.wasegmul.gamification.GamificationManager
import com.agrelius.wasegmul.gamification.XpGainResult
import com.agrelius.wasegmul.EcoImpactCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ClassificationViewModel(
    private val repository: WasteRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private var modelManager: ModelManager? = null

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

    private val _currentRecord = MutableStateFlow<WasteRecord?>(null)
    val currentRecord: StateFlow<WasteRecord?> = _currentRecord

    private val _navigateToResult = Channel<Long>(Channel.CONFLATED)
    val navigateToResult = _navigateToResult.receiveAsFlow()

    fun initModel(context: Context) {
        if (modelManager == null) {
            modelManager = ModelManager(context.applicationContext)
        }
    }

    fun setBitmap(bitmap: Bitmap) {
        // Do NOT manually recycle: Compose may still be drawing the old bitmap
        // during navigation transitions (native crash). Just drop the reference
        // and let GC reclaim. Callers pass a fresh bitmap each time.
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Ensure models are loaded before inference.
                modelManager?.ensureInitialized()

                val outcome = modelManager?.classify(bitmap)
                    ?: run {
                        _error.value = "Models not initialised. Please restart the app."
                        _isLoading.value = false
                        return@launch
                    }

                when (outcome) {
                    is ClassificationOutcome.Success -> {
                        val prediction = outcome.prediction
                        val finalPrediction = MLArbitrator.arbitrate(prediction)
                        val info = WasteKnowledgeBase.getInfo(finalPrediction.category, finalPrediction.subcategory)
                        val isUncertain =
                            finalPrediction.category == WasteMapping.UNCERTAIN ||
                                finalPrediction.category == WasteMapping.UNKNOWN

                        val result = ClassificationResult(
                            category = finalPrediction.category,
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
                        val id = repository.insert(record)
                        _currentRecord.value = record.copy(id = id)

                        // Gamification: calculate XP earned from this scan
                        try {
                            val impact = EcoImpactCalculator.calculate(listOf(record))
                            val co2Grams = impact.co2PreventedKg * 1000.0
                            val dailyCount = settingsManager.incrementDailyScanCount()
                            val currentXp = settingsManager.totalXp.first()
                            val xpResult = GamificationManager.processNewScan(
                                currentTotalXp = currentXp,
                                category = result.category,
                                confidence = result.confidence,
                                co2PreventedGrams = co2Grams,
                                dailyScanCount = dailyCount
                            )
                            settingsManager.setTotalXp(xpResult.newTotalXp)
                            _lastXpGain.value = xpResult
                        } catch (e: Exception) {
                            Log.w(TAG, "XP calculation failed", e)
                        }

                        _isFreshScan.value = true
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

    fun clearError() {
        _error.value = null
    }

    fun releaseBitmap() {
        // Drop reference only; do not recycle (Compose may still hold it).
        _capturedBitmap.value = null
    }

    fun setFeedback(feedback: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch {
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

    fun setCorrection(correction: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch {
            try {
                val rows = repository.updateCorrectionWithCategory(recordId, correction)
                if (rows > 0) {
                    val correctedCategory = WasteMapping.getCategory(correction)
                    _currentRecord.value = _currentRecord.value?.copy(
                        correctedSubclass = correction,
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
        viewModelScope.launch {
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
                val isBarcode = record.featureVector?.startsWith("barcode:") == true
                val classificationMsg = if (isBarcode) {
                    val raw = record.featureVector?.removePrefix("barcode:") ?: ""
                    val parts = raw.split("|", limit = 2)
                    val code = parts.getOrNull(0) ?: ""
                    val prodName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    if (prodName != null) {
                        "Product verified via barcode: $prodName ($code). Packaging classification: ${record.subclass} (${record.category})."
                    } else {
                        "Product verified via barcode ($code). Ground-truth packaging classification: ${record.subclass} (${record.category})."
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
        modelManager?.close()
        _capturedBitmap.value = null
    }

    fun consumeXpGain() {
        _lastXpGain.value = null
    }

    class Factory(
        private val repository: WasteRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClassificationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ClassificationViewModel(repository, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "ClassificationVM"
    }
}
