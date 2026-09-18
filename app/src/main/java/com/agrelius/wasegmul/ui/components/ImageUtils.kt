package com.agrelius.wasegmul.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Shared sampled-bitmap decoder (single copy used by Home + Classify).
 *
 * Decodes bounds first, then a downsampled ARGB_8888 bitmap, applying EXIF
 * rotation. Call off the Main thread (Dispatchers.IO). Returns null — never
 * throws — so callers can surface a snackbar instead of crashing.
 */
fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }

        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(rawBitmap, 90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(rawBitmap, 180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(rawBitmap, 270f)
            else -> rawBitmap
        }
    } catch (e: Exception) {
        android.util.Log.e("ImageUtils", "Failed to decode sampled bitmap", e)
        null
    }
}

/** Returns true when [uri] can still be opened (guards stale process-death URIs). */
fun uriExists(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
