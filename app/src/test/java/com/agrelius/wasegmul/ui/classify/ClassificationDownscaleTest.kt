package com.agrelius.wasegmul.ui.classify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the silent-kill guard on the Capture-and-Classify path:
 * full-resolution camera frames must be bounded before they are retained or
 * fed to inference, so an oversized bitmap surfaces a visible error instead of
 * killing the process (OOM/LMK).
 */
class ClassificationDownscaleTest {

    @Test
    fun downscaleTargetSize_smallImage_unchanged() {
        assertEquals(
            800 to 600,
            ClassificationViewModel.downscaleTargetSize(800, 600)
        )
    }

    @Test
    fun downscaleTargetSize_atLimit_unchanged() {
        assertEquals(
            1024 to 1024,
            ClassificationViewModel.downscaleTargetSize(1024, 1024)
        )
    }

    @Test
    fun downscaleTargetSize_fullFrameLandscape_scaledToLimitPreservingAspect() {
        // 4000x3000 -> scale 1024/4000 -> 1024x768.
        assertEquals(
            1024 to 768,
            ClassificationViewModel.downscaleTargetSize(4000, 3000)
        )
    }

    @Test
    fun downscaleTargetSize_fullFramePortrait_scaledToLimitPreservingAspect() {
        // 3000x4000 -> scale 1024/4000 -> 768x1024.
        assertEquals(
            768 to 1024,
            ClassificationViewModel.downscaleTargetSize(3000, 4000)
        )
    }
}
