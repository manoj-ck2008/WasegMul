package com.agrelius.wasegmul.ui.classify

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.data.ClassificationResult
import com.agrelius.wasegmul.data.WasteRecord
import com.agrelius.wasegmul.knowledge.WasteKnowledgeBase
import com.agrelius.wasegmul.ml.ModelManager
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.SettingsManager
import com.agrelius.wasegmul.utils.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ClassificationViewModel(
    private val repository: WasteRepository,
    private val soundManager: SoundManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private var modelManager: ModelManager? = null
    private val CONFIDENCE_THRESHOLD = 0.50f

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
                val prediction = modelManager?.classify(bitmap)
                if (prediction != null) {
                    val info = WasteKnowledgeBase.getInfo(prediction.category, prediction.subcategory)
                    val isUncertain = prediction.subcategoryConfidence < CONFIDENCE_THRESHOLD
                    
                    val result = ClassificationResult(
                        category = if (isUncertain) "Uncertain" else prediction.category,
                        subclass = prediction.subcategory,
                        confidence = prediction.categoryConfidence,
                        topPredictions = prediction.topSubcategories,
                        disposalGuide = if (isUncertain) 
                            "CAUTION: Low confidence match detected.\n\n" + info.disposalGuide 
                            else info.disposalGuide,
                        environmentalImpact = info.environmentalImpact,
                        recyclingBenefits = info.recyclingBenefits
                    )

                    _classificationResult.value = result
                    
                    if (isUncertain) {
                        soundManager.playWarning()
                    } else {
                        soundManager.playSuccess()
                    }

                    // Save to history
                    val estimatedWeight = if (isUncertain) 0.0 else WasteKnowledgeBase.getEstimatedWeight(result.subclass)
                    val featureVector = "vec_" + UUID.randomUUID().toString().take(8)

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
                        featureVector = featureVector,
                        imagePath = savedPath
                    )
                    val id = repository.insert(record)
                    _currentRecord.value = record.copy(id = id)
                } else {
                    _error.value = "Hardware Sync Error"
                }
            } catch (e: Exception) {
                _error.value = "System Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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
            // Update local state
            _currentRecord.value = _currentRecord.value?.copy(feedback = feedback)
        }
    }

    fun setCorrection(correction: String) {
        val recordId = _currentRecord.value?.id ?: return
        viewModelScope.launch {
            repository.updateCorrection(recordId, correction)
            // Update local state
            _currentRecord.value = _currentRecord.value?.copy(correctedSubclass = correction)
        }
    }

    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            repository.allHistory.first().find { it.id == recordId }?.let { record ->
                val info = WasteKnowledgeBase.getInfo(record.category, record.subclass)
                _classificationResult.value = ClassificationResult(
                    category = record.category,
                    subclass = record.subclass,
                    confidence = record.confidence,
                    topPredictions = emptyList(),
                    disposalGuide = info.disposalGuide,
                    environmentalImpact = info.environmentalImpact,
                    recyclingBenefits = info.recyclingBenefits
                )
                _currentRecord.value = record
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelManager?.close()
    }

    class Factory(
        private val repository: WasteRepository,
        private val soundManager: SoundManager,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClassificationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ClassificationViewModel(repository, soundManager, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
