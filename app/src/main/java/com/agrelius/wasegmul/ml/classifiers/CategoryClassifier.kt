package com.agrelius.wasegmul.ml.classifiers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.agrelius.wasegmul.ml.InternalResult
import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class CategoryClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = FileUtil.loadMappedFile(context, "category_model_finetuned.tflite")
        labels = FileUtil.loadLabels(context, "category_classes.txt")
        
        val options = Interpreter.Options()
        interpreter = Interpreter(model, options)

        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d("CategoryClassifier", "Output shape: ${outputShape.contentToString()}, Labels size: ${labels.size}")
    }

    fun classify(bitmap: Bitmap): InternalResult {
        val tensorImage = ImagePreprocessor.preprocess(bitmap)
        
        val outputBuffer = TensorBuffer.createFixedSize(
            interpreter.getOutputTensor(0).shape(), 
            interpreter.getOutputTensor(0).dataType()
        )
        
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())
        
        val labeledProbability = TensorLabel(labels, outputBuffer).mapWithFloatValue
        val topResult = labeledProbability.maxByOrNull { it.value }
        
        return InternalResult(
            label = topResult?.key ?: "Unknown",
            confidence = topResult?.value ?: 0f
        )
    }

    fun close() {
        interpreter.close()
    }
}
