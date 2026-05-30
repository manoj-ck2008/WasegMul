package com.agrelius.wasegmul.ml.classifiers

import android.content.Context
import android.graphics.Bitmap
import com.agrelius.wasegmul.ml.InternalResult
import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class SubclassClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = FileUtil.loadMappedFile(context, "subclass_model_finetuned.tflite")
        labels = FileUtil.loadLabels(context, "subclass_classes.txt")
        
        val options = Interpreter.Options()
        // GPU delegate removed to prevent NoClassDefFoundError
        
        interpreter = Interpreter(model, options)
    }

    fun classify(bitmap: Bitmap): InternalResult {
        val tensorImage = ImagePreprocessor.preprocess(bitmap)
        
        val outputBuffer = TensorBuffer.createFixedSize(
            interpreter.getOutputTensor(0).shape(), 
            interpreter.getOutputTensor(0).dataType()
        )
        
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())
        
        val labeledProbability = TensorLabel(labels, outputBuffer).mapWithFloatValue
        
        val topEntries = labeledProbability.entries
            .sortedByDescending { it.value }
            .take(5)

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
}
