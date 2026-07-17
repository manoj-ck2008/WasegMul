package com.agrelius.wasegmul.ml.preprocessing

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Preprocessing for the EfficientNet-based category & subclass models.
 *
 * EfficientNet models trained for this project handle input scaling internally, so we feed
 * raw [0, 255] FLOAT32 pixels after a center-crop-then-resize to 224x224.
 *
 * The center-crop preserves aspect ratio: a tall portrait image is cropped to its central
 * square region before resizing, preventing the distortion that a raw stretch would cause.
 *
 * This object is thread-safe: each call to [preprocess] builds its own [ResizeOp] internally.
 */
object ImagePreprocessor {
    private const val INPUT_SIZE = 224

    fun preprocess(bitmap: Bitmap): TensorImage {
        val cropped = centerCropToSquare(bitmap)
        return try {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(cropped)
            val processor = org.tensorflow.lite.support.image.ImageProcessor.Builder()
                .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            processor.process(tensorImage)
        } finally {
            if (cropped !== bitmap) cropped.recycle()
        }
    }

    /**
     * Center-crops [bitmap] to its largest inscribed square.
     * If the bitmap is already square, returns it unchanged.
     */
    private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == h) return bitmap

        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
}
