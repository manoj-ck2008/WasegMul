package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.PredictionResult
import com.agrelius.wasegmul.ml.classifiers.CategoryClassifier
import com.agrelius.wasegmul.ml.classifiers.SubclassClassifier
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class ClassificationOutcome {
    data class Success(val prediction: PredictionResult) : ClassificationOutcome()
    data class Failure(val reason: FailureReason, val message: String, val cause: Throwable? = null) : ClassificationOutcome()
}

enum class FailureReason {
    MODEL_LOAD_FAILED,
    CATEGORY_INFERENCE_FAILED,
    SUBCLASS_INFERENCE_FAILED,
    NOT_INITIALIZED,
    UNKNOWN
}

class ModelManager(private val context: Context) {

    private var categoryClassifier: CategoryClassifier? = null
    private var subclassClassifier: SubclassClassifier? = null

    @Volatile
    private var isInitialized = false

    private val initMutex = Mutex()

    suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            try {
                TfLite.initialize(context).await()
                categoryClassifier = CategoryClassifier(context.applicationContext)
                subclassClassifier = SubclassClassifier(context.applicationContext)
                isInitialized = true
                Log.d(TAG, "Classifiers initialised via Play Services successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise classifiers", e)
                cleanup()
                throw ModelInitException("Could not load ML models", e)
            }
        }
    }

    suspend fun classify(bitmap: Bitmap): ClassificationOutcome = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext ClassificationOutcome.Failure(
                FailureReason.NOT_INITIALIZED,
                "Models have not been initialised. Please restart the app.",
                null
            )
        }

        try {
            val catResult = categoryClassifier?.classify(bitmap)
            if (catResult == null) {
                Log.e(TAG, "Category classifier returned null")
                return@withContext ClassificationOutcome.Failure(
                    FailureReason.CATEGORY_INFERENCE_FAILED,
                    "The category model failed to process the image. Please try again with a clearer photo.",
                    null
                )
            }

            val subResult = subclassClassifier?.classify(bitmap)
            if (subResult == null) {
                Log.e(TAG, "Subclass classifier returned null")
                return@withContext ClassificationOutcome.Failure(
                    FailureReason.SUBCLASS_INFERENCE_FAILED,
                    "The subclass model failed to process the image. Please try again with a clearer photo.",
                    null
                )
            }

            ClassificationOutcome.Success(
                PredictionResult(
                    category = catResult.label,
                    categoryConfidence = catResult.confidence,
                    subcategory = subResult.label,
                    subcategoryConfidence = subResult.confidence,
                    topSubcategories = subResult.topPredictions
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification error during inference", e)
            ClassificationOutcome.Failure(
                FailureReason.UNKNOWN,
                "Classification failed: ${e.message ?: "Unknown error"}. Please try again.",
                e
            )
        }
    }

    fun close() { cleanup() }

    private fun cleanup() {
        runCatching { categoryClassifier?.close() }
        runCatching { subclassClassifier?.close() }
        categoryClassifier = null
        subclassClassifier = null
        isInitialized = false
    }

    companion object { private const val TAG = "ModelManager" }
}

class ModelInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
