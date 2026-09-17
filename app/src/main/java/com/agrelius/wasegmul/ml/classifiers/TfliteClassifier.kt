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
 * Generic reusable wrapper for TFLite image classification models.
 * Encapsulates model loading, output buffer allocation, synchronized inference,
 * and top-K prediction extraction.
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

    init {
        val model = FileUtil.loadMappedFile(context, modelFilename)
        labels = FileUtil.loadLabels(context, labelsFilename)

        val options = Interpreter.Options().apply { setNumThreads(numThreads) }
        val interp = Interpreter(model, options)

        val outputShape = interp.getOutputTensor(0).shape()
        if (outputShape.last() != labels.size) {
            runCatching { interp.close() }
            require(outputShape.last() == labels.size) {
                "$tag output shape ${outputShape.contentToString()} does not match " +
                    "${labels.size} labels in $labelsFilename"
            }
        }
        outputBuffer = TensorBuffer.createFixedSize(outputShape, interp.getOutputTensor(0).dataType())
        interpreter = interp
        Log.d(tag, "Loaded $modelFilename: output shape ${outputShape.contentToString()}, labels: ${labels.size}")
    }

    private val lock = Any()

    fun classify(bitmap: Bitmap): InternalResult = synchronized(lock) {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap has zero dimensions" }
        val interp = interpreter ?: throw IllegalStateException("Classifier has been closed")
        require(!closed) { "Classifier has been closed" }

        val tensorImage = ImagePreprocessor.preprocess(bitmap)
        interp.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val labeledProbability = TensorLabel(labels, outputBuffer).mapWithFloatValue

        val topEntries = labeledProbability.entries
            .sortedByDescending { it.value }
            .take(topK)

        val topResult = topEntries.firstOrNull()

        InternalResult(
            label = topResult?.key ?: "Unknown",
            confidence = topResult?.value ?: 0f,
            topPredictions = topEntries.map { it.key to it.value }
        )
    }

    fun close() = synchronized(lock) {
        closed = true
        runCatching { interpreter?.close() }
        interpreter = null
    }
}
