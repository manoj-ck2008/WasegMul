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
import com.agrelius.wasegmul.utils.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ClassificationViewModel(
    private val repository: WasteRepository,
    private val soundManager: SoundManager
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

    private var currentRecordId: Long = -1

    fun initModel(context: Context) {
        if (modelManager == null) {
            modelManager = ModelManager(context.applicationContext)
        }
    }

    fun setBitmap(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _classificationResult.value = null
        _error.value = null
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
                    
                    // AUDITORY FEEDBACK
                    if (isUncertain) {
                        soundManager.playWarning()
                    } else {
                        soundManager.playSuccess()
                    }

                    // Save to history - persistent storage
                    val estimatedWeight = if (isUncertain) 0.0 else WasteKnowledgeBase.getEstimatedWeight(result.subclass)
                    
                    currentRecordId = repository.insert(WasteRecord(
                        category = result.category,
                        subclass = result.subclass,
                        confidence = result.confidence,
                        estimatedWeight = estimatedWeight
                    ))
                } else {
                    _error.value = "Hardware Sync: Interface Error"
                }
            } catch (e: Exception) {
                _error.value = "System Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFeedback(feedback: String) {
        if (currentRecordId != -1L) {
            viewModelScope.launch {
                repository.updateFeedback(currentRecordId, feedback)
            }
        }
    }

    fun setCorrection(correction: String) {
        if (currentRecordId != -1L) {
            viewModelScope.launch {
                repository.updateCorrection(currentRecordId, correction)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelManager?.close()
        // Note: releasing soundManager here as it's shared/provided by Activity context via App
    }

    class Factory(
        private val repository: WasteRepository,
        private val soundManager: SoundManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClassificationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ClassificationViewModel(repository, soundManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
