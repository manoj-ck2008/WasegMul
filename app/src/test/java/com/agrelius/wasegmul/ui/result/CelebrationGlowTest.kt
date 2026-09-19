package com.agrelius.wasegmul.ui.result

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the Result-screen kill on entry:
 * `ThankYouOverlay` drew a `Brush.radialGradient` with the glow animation's
 * starting radius of 0, and `android.graphics.RadialGradient` throws
 * `IllegalArgumentException: ending radius must be > 0`.
 *
 * Independent truth: the platform constraint (radius must be strictly
 * positive and finite) — not the implementation. The overlay must skip the
 * glow pass for 0 / negative / non-finite radii and draw for positive ones.
 */
class CelebrationGlowTest {

    @Test
    fun zeroRadius_skipsGlowPass() {
        assertFalse(shouldDrawCelebrationGlow(0f))
    }

    @Test
    fun negativeRadius_skipsGlowPass() {
        assertFalse(shouldDrawCelebrationGlow(-12f))
    }

    @Test
    fun nonFiniteRadius_skipsGlowPass() {
        assertFalse(shouldDrawCelebrationGlow(Float.NaN))
        assertFalse(shouldDrawCelebrationGlow(Float.POSITIVE_INFINITY))
    }

    @Test
    fun positiveRadius_drawsGlowPass() {
        assertTrue(shouldDrawCelebrationGlow(0.5f))
        assertTrue(shouldDrawCelebrationGlow(640f))
    }
}
