package com.agrelius.wasegmul.ml.preprocessing

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
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
 * This object is thread-safe and reuses an immutable, pre-built [ImageProcessor].
 */
object ImagePreprocessor {
    private const val INPUT_SIZE = 224

    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun preprocess(bitmap: Bitmap): TensorImage {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap has zero dimensions" }

        // HARDWARE bitmaps cannot be read directly by software renderers/native ops
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val cropped = centerCropToSquare(softwareBitmap)
        return try {
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(cropped)
            imageProcessor.process(tensorImage)
        } finally {
            if (cropped !== bitmap && cropped !== softwareBitmap) {
                cropped.recycle()
            }
            if (softwareBitmap !== bitmap) {
                softwareBitmap.recycle()
            }
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
