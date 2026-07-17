package com.agrelius.wasegmul.ml.classifiers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.InternalResult
import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.jvm.Volatile

/**
 * Wraps the EfficientNet category model and maps its softmax output to one of the four
 * category labels (E-Waste, Organic, Recyclable, Trash).
 *
 * Returns the top-[TOP_K] predictions so the arbitrator can assess category-model
 * uncertainty (e.g. when two categories have close probabilities).
 *
 * Inference runs with [NUM_THREADS] CPU threads; the interpreter is closed via [close].
 */
class CategoryClassifier(context: Context) {

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var closed = false
    private val labels: List<String>
    private val outputBuffer: TensorBuffer

    init {
        val model = FileUtil.loadMappedFile(context, "category_model_finetuned.tflite")
        labels = FileUtil.loadLabels(context, "category_classes.txt")

        val options = Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
        val interp = Interpreter(model, options)

        val outputShape = interp.getOutputTensor(0).shape()
        if (outputShape.last() != labels.size) {
            runCatching { interp.close() }
            require(outputShape.last() == labels.size) {
                "Category model output shape ${outputShape.contentToString()} does not match " +
                    "${labels.size} labels in category_classes.txt"
            }
        }
        outputBuffer = TensorBuffer.createFixedSize(outputShape, interp.getOutputTensor(0).dataType())
        interpreter = interp
        Log.d(TAG, "Output shape: ${outputShape.contentToString()}, labels size: ${labels.size}")
    }

    fun classify(bitmap: Bitmap): InternalResult {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap has zero dimensions" }
        val interp = interpreter ?: throw IllegalStateException("Classifier has been closed")
        require(!closed) { "Classifier has been closed" }

        val tensorImage = ImagePreprocessor.preprocess(bitmap)
        interp.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val labeledProbability = TensorLabel(labels, outputBuffer).mapWithFloatValue

        val topEntries = labeledProbability.entries
            .sortedByDescending { it.value }
            .take(TOP_K)

        val topResult = topEntries.firstOrNull()

        return InternalResult(
            label = topResult?.key ?: "Unknown",
            confidence = topResult?.value ?: 0f,
            topPredictions = topEntries.map { it.key to it.value }
        )
    }

    fun close() {
        closed = true
        runCatching { interpreter?.close() }
        interpreter = null
    }

    companion object {
        private const val TAG = "CategoryClassifier"
        private const val NUM_THREADS = 2
        private const val TOP_K = 2
    }
}
