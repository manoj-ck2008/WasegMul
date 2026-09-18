package com.agrelius.wasegmul.ml.preprocessing

import android.graphics.Bitmap
import android.graphics.Matrix
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Preprocessing for the EfficientNet-based category & subclass models.
 *
 * ### Normalization contract (§3.33 — DO NOT CHANGE WITHOUT TRAINING TRUTH)
 * This pipeline feeds raw **[0, 255] FLOAT32** pixels after center-crop-then-resize
 * to 224×224, with **no `NormalizeOp`**. That is correct ONLY IF the EfficientNet
 * checkpoints were trained on raw `[0,255]` inputs (internal rescaling). If training
 * used `preprocess_input` / rescale-to-`[0,1]`, accuracy collapses and the fix is to
 * add the matching `NormalizeOp` HERE plus a regression test — never to "fix" the
 * README alone.
 *
 * TODO(accuracy): confirm against `scripts/kaggle_train.py` preprocessing config;
 * then either (a) keep raw + correct `README.md:182,192` (`0.0–1.0` claim), or
 * (b) add `NormalizeOp(0f, 255f)`-equivalent + assert via [assertOutputRange].
 * Until then, [assertOutputRange] pins current behavior so drift is caught, not silent.
 *
 * The center-crop preserves aspect ratio: a tall portrait image is cropped to its central
 * square region before resizing, preventing the distortion that a raw stretch would cause.
 *
 * ### Rotation contract
 * EXIF orientation is NOT read here. **Callers MUST pass [rotationDegrees]** (CameraX
 * `imageInfo.rotationDegrees`) or pre-rotate to upright — a sideways portrait frame
 * center-crops the wrong region. Rotation is applied BEFORE the crop.
 *
 * This object is thread-safe and reuses an immutable, pre-built [ImageProcessor].
 */
object ImagePreprocessor {
    const val INPUT_SIZE = 224

    /**
     * Maximum pixel value the pipeline emits. Raw `[0,255]` today; a normalization
     * change MUST update this const, the KDoc above, and the training config together.
     */
    const val OUTPUT_MAX = 255.0f

    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    /**
     * @param rotationDegrees clockwise rotation applied before crop (CameraX
     *   `imageInfo.rotationDegrees`). `0` = bitmap already upright.
     * @throws IllegalArgumentException for recycled/empty bitmaps.
     * @throws IllegalStateException when a HARDWARE bitmap cannot be copied to software.
     */
    fun preprocess(bitmap: Bitmap, rotationDegrees: Int = 0): TensorImage {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap has zero dimensions" }

        try {
            return preprocessInternal(bitmap, rotationDegrees)
        } catch (e: OutOfMemoryError) {
            // Convert the kill into a catchable failure: ModelManager maps this
            // to ClassificationOutcome.Failure with a "too large" message.
            throw IllegalStateException(
                "Image too large to preprocess; try a smaller photo.", e
            )
        }
    }

    private fun preprocessInternal(bitmap: Bitmap, rotationDegrees: Int): TensorImage {
        // HARDWARE bitmaps cannot be read by software renderers/native ops. copy() is a
        // platform-nullable type: null-guard it instead of NPE-crashing (§3.33).
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException(
                    "Could not copy HARDWARE bitmap to ARGB_8888 (GPU-resident frame?)"
                )
        } else {
            bitmap
        }

        var rotated: Bitmap? = null
        try {
            val upright = if (rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
                Bitmap.createBitmap(
                    softwareBitmap, 0, 0, softwareBitmap.width, softwareBitmap.height, matrix, true
                ).also { rotated = it }
            } else {
                softwareBitmap
            }
            val cropped = centerCropToSquare(upright)
            return try {
                val tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(cropped)
                imageProcessor.process(tensorImage)
            } finally {
                if (cropped !== upright) {
                    cropped.recycle()
                }
            }
        } finally {
            rotated?.let { if (it !== softwareBitmap) it.recycle() }
            if (softwareBitmap !== bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    /**
     * Range-assert test hook (§3.33): verifies a processed buffer honors the
     * normalization contract without needing a Bitmap in JVM unit tests. Tests feed
     * production buffers (or synthetic extremes) and assert `[0, OUTPUT_MAX]`.
     *
     * @return the out-of-range count (0 = contract holds).
     */
    fun assertOutputRange(buffer: FloatArray): Int =
        buffer.count { !it.isFinite() || it < 0f || it > OUTPUT_MAX }

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
