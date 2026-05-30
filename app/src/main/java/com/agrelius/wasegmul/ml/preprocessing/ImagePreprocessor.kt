package com.agrelius.wasegmul.ml.preprocessing

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Optimized Preprocessor for EfficientNet-based models.
 * EfficientNet models typically expect [0, 255] range for FLOAT32 if using the 
 * standard preprocessing layer inside the model, OR specific normalization if not.
 * Based on user feedback, removed manual 1/255.0 normalization as it was 
 * causing 'keyboard' bias/incorrect outputs.
 */
object ImagePreprocessor {
    private const val INPUT_SIZE = 224

    fun preprocess(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            // Removed NormalizeOp(0.0f, 255.0f) as EfficientNet trained models 
            // often handle scaling internally or expect raw 0-255 float values.
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        return imageProcessor.process(tensorImage)
    }
}
