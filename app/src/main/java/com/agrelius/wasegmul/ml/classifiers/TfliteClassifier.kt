package com.agrelius.wasegmul.ml.classifiers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.WorkerThread
import com.agrelius.wasegmul.InternalResult
import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.jvm.Volatile
import kotlin.math.exp

/**
 * Generic reusable wrapper for TFLite image classification models.
 * Encapsulates model loading, output buffer allocation, synchronized inference,
 * and top-K prediction extraction.
 *
 * ### Threading
 * Construction mmaps the model (call off Main — [ModelManager] uses
 * `Dispatchers.IO`). [classify]/[classifyTensor] are `@WorkerThread` and
 * internally serialized; never call from Main. There is no suspend factory on this
 * class by design: [ModelManager.ensureInitialized] is the single suspend entry
 * point that constructs both classifiers off-Main.
 *
 * ### Output contract (§3.35)
 * Emitted `confidence` values are calibrated softmax probabilities in `0..1`.
 * Two guards enforce that: (1) strict output-shape assertion (rank 2, batch 1,
 * classes == labels — a swapped 4-class/15-class model fails fast instead of
 * mislabeling); (2) dtype-aware dequantization (UINT8 outputs are rescaled via the
 * tensor's own scale/zero-point, not read as raw bytes); (3) uncalibrated logits
 * (any value outside `0..1`) are softmaxed over the FULL output before top-K.
 */
open class TfliteClassifier(
    context: Context,
    val modelFilename: String,
    val labelsFilename: String,
    val topK: Int = 5,
    val numThreads: Int = 2,
    private val tag: String = "TfliteClassifier"
) {
    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var closed = false
    val labels: List<String>
    private val outputBuffer: TensorBuffer
    private val outputQuantScale: Float
    private val outputQuantZeroPoint: Int

    init {
        val model = FileUtil.loadMappedFile(context, modelFilename)
        labels = FileUtil.loadLabels(context, labelsFilename)

        val options = Interpreter.Options().apply { setNumThreads(numThreads) }
        val interp = Interpreter(model, options)

        // Strict shape assert (§3.35): rank 2 + batch 1 + classes == labels. The old
        // check (`last() == labels.size` only) let a swapped category/subclass model
        // pair load and silently mislabel every frame.
        val outputShape = interp.getOutputTensor(0).shape()
        val outputDtype = interp.getOutputTensor(0).dataType()
        if (outputShape.size != 2 || outputShape[0] != 1 || outputShape.last() != labels.size) {
            runCatching { interp.close() }
            throw IllegalArgumentException(
                "$tag output shape ${outputShape.contentToString()} (dtype=$outputDtype) does not match " +
                    "${labels.size} labels in $labelsFilename: expected [1, ${labels.size}]. " +
                    "The model file and labels file are mismatched (e.g. swapped category/subclass models)."
            )
        }
        outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDtype)
        // Snapshot quant params for dtype-aware dequant (FLOAT32 → identity).
        if (outputDtype == DataType.FLOAT32) {
            outputQuantScale = 1f
            outputQuantZeroPoint = 0
        } else {
            val qp = interp.getOutputTensor(0).quantizationParams()
            outputQuantScale = qp.scale
            outputQuantZeroPoint = qp.zeroPoint
        }
        interpreter = interp
        Log.d(tag, "Loaded $modelFilename: output shape ${outputShape.contentToString()}, labels: ${labels.size}")
    }

    private val lock = Any()

    @WorkerThread
    fun classify(bitmap: Bitmap): InternalResult {
        val tensorImage = ImagePreprocessor.preprocess(bitmap)
        return classifyTensor(tensorImage)
    }

    /** Zero-redundancy path: caller pre-processed once and shares across classifiers. */
    @WorkerThread
    fun classifyTensor(tensorImage: org.tensorflow.lite.support.image.TensorImage): InternalResult = synchronized(lock) {
        val interp = interpreter ?: throw IllegalStateException("Classifier has been closed")
        require(!closed) { "Classifier has been closed" }

        interp.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val probabilities: Map<String, Float> =
            if (outputBuffer.dataType == DataType.FLOAT32) {
                dequantizedOrDirect(TensorLabel(labels, outputBuffer).mapWithFloatValue)
            } else {
                // Dtype-aware dequant (§3.35): mapWithFloatValue must not be trusted
                // blindly for quantized buffers — rescale via the snapshotted params.
                dequantizeUint8Output()
            }

        val calibrated = probabilities.let { probs ->
            // Logit calibration (§3.35): uncalibrated logits are softmaxed over the
            // FULL distribution first. Softmaxing only top-K would fabricate shares.
            if (probs.values.any { !it.isFinite() || it < 0f || it > 1f }) {
                Log.w(tag, "Output looks like logits; applying full-distribution softmax")
                softmax(probs)
            } else {
                probs
            }
        }

        val topEntries = calibrated.entries
            .sortedByDescending { it.value }
            .take(topK)

        val topResult = topEntries.firstOrNull()

        InternalResult(
            label = topResult?.key ?: "Unknown",
            confidence = topResult?.value ?: 0f,
            topPredictions = topEntries.map { it.key to it.value }
        )
    }

    /**
     * Identity for float models; clamps stray non-finite values to keep the
     * downstream `require(finite)` guards in shared `InternalResult` honest.
     */
    private fun dequantizedOrDirect(probs: Map<String, Float>): Map<String, Float> =
        probs.mapValues { (_, v) -> if (v.isFinite()) v else 0f }

    private fun dequantizeUint8Output(): Map<String, Float> {
        require(outputQuantScale != 0f) { "$tag: quantized output scale is 0" }
        val raw = outputBuffer.buffer
        raw.rewind()
        return labels.associateWith {
            val q = raw.get().toInt() and 0xFF
            outputQuantScale * (q - outputQuantZeroPoint)
        }
    }

    fun close() = synchronized(lock) {
        closed = true
        runCatching { interpreter?.close() }
        interpreter = null
    }

    companion object {
        /** Full-distribution softmax (pure; unit-testable). Uncalibrated logits in, shares out. */
        fun softmax(logits: Map<String, Float>): Map<String, Float> {
            if (logits.isEmpty()) return emptyMap()
            val max = logits.values.filter { it.isFinite() }.maxOrNull() ?: 0f
            val exps = logits.mapValues { (_, v) ->
                if (!v.isFinite()) 0f else exp((v - max).coerceIn(-50f, 50f))
            }
            val sum = exps.values.sum()
            if (sum <= 0f || !sum.isFinite()) {
                val uniform = 1f / logits.size
                return logits.mapValues { uniform }
            }
            return exps.mapValues { (_, e) -> (e / sum).coerceIn(0f, 1f) }
        }
    }
}
