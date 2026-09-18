package com.agrelius.wasegmul.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.PredictionResult
import com.agrelius.wasegmul.ml.classifiers.CategoryClassifier
import com.agrelius.wasegmul.ml.classifiers.SubclassClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
 *
 * ### Abstain contract
 * A `Success` carrying `category (or subcategory) = "Unknown"` with confidence `0f`
 * is an explicit ABSTAIN, not a prediction: exactly one model failed and the
 * downstream arbitrator must treat that side as absent (low-confidence / uncertain
 * path), never as a voted label. Callers must not award full XP or persist such
 * halves as ground truth.
 *
 * ### Threading
 * A SINGLE [stateMutex] guards init + classify + close (split mutexes previously
 * let `close()` race inference). `close()` is synchronous so it uses `tryLock`;
 * the in-flight `classify()` cleans up in `finally` when close loses the race.
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

    private val stateMutex = Mutex()

    /** Bounded init: mmap of two EfficientNets must not hang launch forever. */
    suspend fun ensureInitialized() {
        if (isInitialized) return
        stateMutex.withLock {
            if (isInitialized) return
            try {
                // Heavy mmap + init MUST NOT run on Main; bounded so a corrupt asset
                // fails fast instead of stalling the app.
                withTimeout(INIT_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        // Play Services TFLite init removed: classifiers use bundled
                        // org.tensorflow.lite.Interpreter, not InterpreterApi.
                        categoryClassifier = CategoryClassifier(context.applicationContext)
                        subclassClassifier = SubclassClassifier(context.applicationContext)
                    }
                }
                isInitialized = true
                Log.d(TAG, "Classifiers initialised successfully")
            } catch (e: CancellationException) {
                // Structured-concurrency contract: never wrap cancellation — clean up
                // and rethrow so the caller's Job actually cancels.
                cleanup()
                throw e
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

        stateMutex.withLock {
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

                // Single shared pre-process: both EfficientNets take identical 224x224 input.
                // Saves one center-crop + resize + 602KB buffer alloc per frame (~30-50% latency).
                val sharedInput = try {
                    com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor.preprocess(bitmap)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Shared preprocessing failed", e)
                    return@withLock ClassificationOutcome.Failure(
                        FailureReason.UNKNOWN,
                        "Image preprocessing failed: ${e.message}. Please try another photo.",
                        e
                    )
                }

                coroutineScope {
                    val catJob = async {
                        try {
                            if (catClassifier != null) catClassifier.classifyTensor(sharedInput) to null
                            else null to "Category classifier not available (closed)"
                        } catch (e: Exception) {
                            Log.e(TAG, "Category classifier threw", e)
                            null to (e.message ?: "Category classifier error")
                        }
                    }
                    val subJob = async {
                        try {
                            if (subClassifier != null) subClassifier.classifyTensor(sharedInput) to null
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

                // Both failed -> total failure. (Dead branches removed: reaching here
                // with exactly one error set is impossible — both results are null —
                // so the reason is always UNKNOWN with the combined detail.)
                if (catResult == null && subResult == null) {
                    val detail = listOfNotNull(catError, subError).joinToString("; ")
                    Log.e(TAG, "Both classifiers failed: $detail")
                    return@withLock ClassificationOutcome.Failure(
                        FailureReason.UNKNOWN,
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
        if (stateMutex.tryLock()) {
            try {
                cleanup()
            } finally {
                stateMutex.unlock()
            }
        }
        // If stateMutex is currently held, the in-flight classify()
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

    companion object {
        private const val TAG = "ModelManager"

        /**
         * Upper bound for the two-model mmap + warmup in [ensureInitialized].
         * Exceeding it means a corrupt/partial asset, not a slow device.
         */
        const val INIT_TIMEOUT_MS = 60_000L
    }
}

class ModelInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
