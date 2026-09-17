package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.PredictionResult
import com.agrelius.wasegmul.ml.classifiers.CategoryClassifier
import com.agrelius.wasegmul.ml.classifiers.SubclassClassifier
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/**
 * Manages the TFLite model lifecycle and runs inference through both classifiers.
 *
 * Supports **degraded classification**: if one classifier fails at inference time,
 * the other's output is still used. The [MLArbitrator] downstream handles the
 * reduced-confidence result. Both models must fail for a total [ClassificationOutcome.Failure].
 */
class ModelManager(private val context: Context) {

    @Volatile
    private var categoryClassifier: CategoryClassifier? = null
    @Volatile
    private var subclassClassifier: SubclassClassifier? = null

    @Volatile
    private var isInitialized = false
    @Volatile
    private var closed = false

    private val initMutex = Mutex()
    private val classifyMutex = Mutex()

    suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            try {
                // Heavy mmap + init MUST NOT run on Main.
                withContext(Dispatchers.IO) {
                    // Play Services TFLite init removed: classifiers use bundled
                    // org.tensorflow.lite.Interpreter, not InterpreterApi.
                    categoryClassifier = CategoryClassifier(context.applicationContext)
                    subclassClassifier = SubclassClassifier(context.applicationContext)
                }
                isInitialized = true
                Log.d(TAG, "Classifiers initialised successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise classifiers", e)
                cleanup()
                throw ModelInitException("Could not load ML models", e)
            }
        }
    }

    /**
     * Runs both classifiers on [bitmap].  If one classifier fails the other's
     * output is used (degraded mode).  Only returns [ClassificationOutcome.Failure]
     * when **both** classifiers fail or the models are not initialised.
     */
    suspend fun classify(bitmap: Bitmap): ClassificationOutcome = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext ClassificationOutcome.Failure(
                FailureReason.NOT_INITIALIZED,
                "Models have not been initialised. Please restart the app.",
                null
            )
        }

        classifyMutex.withLock {
            if (closed) {
                return@withLock ClassificationOutcome.Failure(
                    FailureReason.NOT_INITIALIZED,
                    "Models have been closed.",
                    null
                )
            }
            try {
                val catClassifier = categoryClassifier
                val subClassifier = subclassClassifier

                var catResult: com.agrelius.wasegmul.InternalResult? = null
                var subResult: com.agrelius.wasegmul.InternalResult? = null
                var catError: String? = null
                var subError: String? = null

                coroutineScope {
                    val catJob = async {
                        try {
                            if (catClassifier != null) catClassifier.classify(bitmap) to null
                            else null to "Category classifier not available (closed)"
                        } catch (e: Exception) {
                            Log.e(TAG, "Category classifier threw", e)
                            null to (e.message ?: "Category classifier error")
                        }
                    }
                    val subJob = async {
                        try {
                            if (subClassifier != null) subClassifier.classify(bitmap) to null
                            else null to "Subclass classifier not available (closed)"
                        } catch (e: Exception) {
                            Log.e(TAG, "Subclass classifier threw", e)
                            null to (e.message ?: "Subclass classifier error")
                        }
                    }
                    val (catRes, catErr) = catJob.await()
                    val (subRes, subErr) = subJob.await()
                    catResult = catRes; catError = catErr
                    subResult = subRes; subError = subErr
                }

                // Both failed -> total failure.
                if (catResult == null && subResult == null) {
                    val detail = listOfNotNull(catError, subError).joinToString("; ")
                    Log.e(TAG, "Both classifiers failed: $detail")
                    val reason = when {
                        catError != null && subError == null -> FailureReason.CATEGORY_INFERENCE_FAILED
                        subError != null && catError == null -> FailureReason.SUBCLASS_INFERENCE_FAILED
                        else -> FailureReason.UNKNOWN
                    }
                    return@withLock ClassificationOutcome.Failure(
                        reason,
                        "Classification failed: $detail. Please try again.",
                        null
                    )
                }

                if (catError != null) Log.w(TAG, "Degraded mode: category model failed: $catError")
                if (subError != null) Log.w(TAG, "Degraded mode: subclass model failed: $subError")

                ClassificationOutcome.Success(
                    PredictionResult(
                        category = catResult?.label ?: "Unknown",
                        categoryConfidence = catResult?.confidence ?: 0f,
                        subcategory = subResult?.label ?: "Unknown",
                        subcategoryConfidence = subResult?.confidence ?: 0f,
                        topSubcategories = subResult?.topPredictions ?: emptyList(),
                        topCategories = catResult?.topPredictions ?: emptyList()
                    )
                )
            } finally {
                if (closed) {
                    cleanup()
                }
            }
        }
    }

    fun close() {
        closed = true
        isInitialized = false
        if (classifyMutex.tryLock()) {
            try {
                cleanup()
            } finally {
                classifyMutex.unlock()
            }
        }
        // If classifyMutex is currently held, the in-flight classify()
        // will call cleanup() in its finally block once inference finishes.
    }

    private fun cleanup() {
        runCatching { categoryClassifier?.close() }.exceptionOrNull()?.let {
            Log.w(TAG, "Error closing category classifier", it)
        }
        runCatching { subclassClassifier?.close() }.exceptionOrNull()?.let {
            Log.w(TAG, "Error closing subclass classifier", it)
        }
        categoryClassifier = null
        subclassClassifier = null
        isInitialized = false
    }

    companion object { private const val TAG = "ModelManager" }
}

class ModelInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
