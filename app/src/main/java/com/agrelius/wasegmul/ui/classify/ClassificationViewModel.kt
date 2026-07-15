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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ClassificationViewModel(
    private val repository: WasteRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private var modelManager: ModelManager? = null

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap

    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentRecord = MutableStateFlow<WasteRecord?>(null)
    val currentRecord: StateFlow<WasteRecord?> = _currentRecord

    fun initModel(context: Context) {
        if (modelManager == null) {
            modelManager = ModelManager(context.applicationContext)
        }
    }

    fun setBitmap(bitmap: Bitmap) {
        _capturedBitmap.value?.takeIf { !it.isRecycled }?.recycle()
        _capturedBitmap.value = bitmap
        _classificationResult.value = null
        _error.value = null
        _currentRecord.value = null
    }

    fun classify(context: Context) {
        val bitmap = _capturedBitmap.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
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
                            sources = info.sources
                        )

                        _classificationResult.value = result

                        val estimatedWeight = if (isUncertain) 0.0
                        else WasteMapping.getWeight(result.subclass)

                        var savedPath: String? = null
                        val isSharingEnabled = settingsManager.isImageSharingEnabled.first()
                        if (isSharingEnabled) {
                            savedPath = saveImageLocally(context, bitmap)
                        }

                        val record = WasteRecord(
                            category = result.category,
                            subclass = result.subclass,
                            confidence = result.confidence,
                            estimatedWeight = estimatedWeight,
                            featureVector = null,
                            imagePath = savedPath,
                            topPredictions = PredictionCodec.encode(result.topPredictions)
                        )
                        val id = repository.insert(record)
                        _currentRecord.value = record.copy(id = id)
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
                        Log.e(TAG, "Classification failed: ${outcome.reason} — $msg", outcome.cause)
                        _error.value = msg
                    }
                }
            } catch (e: ModelInitException) {
                Log.e(TAG, "Model initialisation failed", e)
                _error.value = "Unable to load the AI models. Restart the app or check available storage."
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during classification", e)
                _error.value = "An unexpected error occurred: ${e.message ?: "Unknown error"}. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun saveImageLocally(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "training_data")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "waste_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun setFeedback(feedback: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch {
            repository.updateFeedback(recordId, feedback)
            _currentRecord.value = _currentRecord.value?.copy(feedback = feedback)
        }
    }

    fun setCorrection(correction: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch {
            repository.updateCorrection(recordId, correction)
            _currentRecord.value = _currentRecord.value?.copy(correctedSubclass = correction)
        }
    }

    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            val record = repository.getRecordById(recordId) ?: return@launch
            val info = WasteKnowledgeBase.getInfo(record.category, record.subclass)
            _classificationResult.value = ClassificationResult(
                category = record.category,
                subclass = record.subclass,
                confidence = record.confidence,
                topPredictions = PredictionCodec.decode(record.topPredictions),
                disposalGuide = info.disposalGuide,
                environmentalImpact = info.environmentalImpact,
                recyclingBenefits = info.recyclingBenefits,
                sources = info.sources
            )
            _currentRecord.value = record
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelManager?.close()
        _capturedBitmap.value?.takeIf { !it.isRecycled }?.recycle()
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
