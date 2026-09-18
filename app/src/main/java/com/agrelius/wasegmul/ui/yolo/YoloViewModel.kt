package com.agrelius.wasegmul.ui.yolo

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agrelius.wasegmul.ml.Detection
import com.agrelius.wasegmul.ml.YoloDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * YOLO live-detection state holder (separate file: previously nested in the
 * screen, which hid the Main-thread contract).
 *
 * Threading: [detectFrame] must be called off the Main thread
 * (Dispatchers.Default). [YoloDetector.detect] is itself Main-safe, but the
 * frame snapshot bookkeeping around it is not free, so callers dispatch.
 */
class YoloViewModel : ViewModel() {
    @Volatile
    private var detector: YoloDetector? = null
    private val initGuard = Mutex()
    private val detectMutex = Mutex()
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile
    private var frameCount = 0
    @Volatile
    private var lastFpsTime = System.currentTimeMillis()

    suspend fun initDetector(context: android.content.Context) {
        initGuard.withLock {
            if (detector != null) return
            try {
                val d = YoloDetector(context.applicationContext)
                d.ensureInitialized()
                detector = d
                _error.value = null
            } catch (e: Exception) {
                detector = null
                _error.value = "YOLO model not available. Check bundled models and storage."
                Log.w("YoloVM", "YOLO init failed: ${e.message}")
            }
        }
    }

    fun applyConfidenceThreshold(value: Float) {
        detector?.setConfidenceThreshold(value)
    }

    suspend fun detectFrame(bitmap: Bitmap) {
        // Drop frames when busy instead of queueing (prevents multi-second lag/OOM).
        if (!detectMutex.tryLock()) return
        try {
            val d = detector ?: return
            try {
                val results = d.detect(bitmap)
                _detections.value = results

                frameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastFpsTime
                if (elapsed >= 1000) {
                    _fps.value = frameCount * 1000f / elapsed
                    frameCount = 0
                    lastFpsTime = now
                }
            } catch (e: Exception) {
                Log.e("YoloVM", "Detection failed", e)
            }
        } finally {
            detectMutex.unlock()
        }
    }

    fun clearError() {
        _error.value = null
        detector?.close()
        detector = null
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return YoloViewModel() as T
        }
    }
}
