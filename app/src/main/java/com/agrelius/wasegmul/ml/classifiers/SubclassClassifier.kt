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

/**
 * Wraps the EfficientNet subclass model and returns the top-[TOP_K] subclass predictions ranked
 * by softmax confidence. Output is consumed by [com.agrelius.wasegmul.MLArbitrator].
 *
 * Inference runs with [NUM_THREADS] CPU threads; the interpreter is closed via [close].
 */
class SubclassClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = FileUtil.loadMappedFile(context, "subclass_model_finetuned.tflite")
        labels = FileUtil.loadLabels(context, "subclass_classes.txt")

        val options = Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
        interpreter = Interpreter(model, options)

        val outputShape = interpreter.getOutputTensor(0).shape()
        if (outputShape.last() != labels.size) {
            runCatching { interpreter.close() }
            require(outputShape.last() == labels.size) {
                "Subclass model output shape ${outputShape.contentToString()} does not match " +
                    "${labels.size} labels in subclass_classes.txt"
            }
        }
        Log.d(TAG, "Output shape: ${outputShape.contentToString()}, labels size: ${labels.size}")
    }

    fun classify(bitmap: Bitmap): InternalResult {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap has zero dimensions" }
        val tensorImage = ImagePreprocessor.preprocess(bitmap)

        val outputBuffer = TensorBuffer.createFixedSize(
            interpreter.getOutputTensor(0).shape(),
            interpreter.getOutputTensor(0).dataType()
        )

        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

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
        interpreter.close()
    }

    companion object {
        private const val TAG = "SubclassClassifier"
        private const val NUM_THREADS = 2
        private const val TOP_K = 5
    }
}
