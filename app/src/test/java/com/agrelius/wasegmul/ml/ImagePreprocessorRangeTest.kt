package com.agrelius.wasegmul.ml

import com.agrelius.wasegmul.ml.preprocessing.ImagePreprocessor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the ImagePreprocessor normalization contract (ADR-0001 §5): the pipeline
 * emits raw [0, OUTPUT_MAX] pixels. A normalization change must update the
 * const, the KDoc and the training config together — this test catches silent drift.
 */
class ImagePreprocessorRangeTest {

    @Test
    fun assertOutputRange_inRangeBuffer_returnsZero() {
        val buffer = floatArrayOf(0f, 1f, 127.5f, 255f)
        assertEquals(0, ImagePreprocessor.assertOutputRange(buffer))
    }

    @Test
    fun assertOutputRange_outOfRangeBuffer_returnsCount() {
        val buffer = floatArrayOf(-1f, 0f, 255f, 256f, Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals(4, ImagePreprocessor.assertOutputRange(buffer))
    }
}
