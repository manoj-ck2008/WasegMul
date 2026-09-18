package com.agrelius.wasegmul.ui.yolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red-phase tests for the YOLO temporal smoother.
 *
 * Spec: live detections flicker (empty frames) and shake (box jitter).
 * The smoother must hold boxes briefly across gaps (min-hold), keep
 * near-threshold boxes for known tracks (hysteresis), and freeze tiny
 * box movements (deadband) — all without Android dependencies.
 */
class DetectionSmootherTest {

    private fun raw(
        label: String = "Plastic",
        confidence: Float = 0.80f,
        left: Float = 0.20f,
        top: Float = 0.20f,
        right: Float = 0.60f,
        bottom: Float = 0.70f
    ) = RawDetection(label, confidence, left, top, right, bottom)

    @Test
    fun holdsBoxAcrossBriefGap() {
        val smoother = DetectionSmoother(holdMs = 600L)
        val first = smoother.update(listOf(raw()), nowMs = 0L)
        assertEquals(1, first.size)

        // Empty frame 400ms later: track must survive (min-hold).
        val held = smoother.update(emptyList(), nowMs = 400L)
        assertEquals(1, held.size)
        assertEquals("Plastic", held[0].label)
    }

    @Test
    fun dropsBoxAfterHoldExpires() {
        val smoother = DetectionSmoother(holdMs = 600L)
        smoother.update(listOf(raw()), nowMs = 0L)

        val gone = smoother.update(emptyList(), nowMs = 601L)
        assertTrue(gone.isEmpty())
    }

    @Test
    fun hysteresisKeepsKnownTrackBelowEnterThreshold() {
        val smoother = DetectionSmoother(enterThreshold = 0.45f, keepThreshold = 0.35f)
        smoother.update(listOf(raw(confidence = 0.80f)), nowMs = 0L)

        // 0.40 is below enter (0.45) but above keep (0.35): known track survives.
        val kept = smoother.update(listOf(raw(confidence = 0.40f)), nowMs = 100L)
        assertEquals(1, kept.size)
    }

    @Test
    fun belowEnterThresholdStartsNoNewTrack() {
        val smoother = DetectionSmoother(enterThreshold = 0.45f, keepThreshold = 0.35f)

        val none = smoother.update(listOf(raw(confidence = 0.40f)), nowMs = 0L)
        assertTrue(none.isEmpty())
    }

    @Test
    fun deadbandFreezesTinyJitter() {
        val smoother = DetectionSmoother(deadbandFrac = 0.02f)
        val first = smoother.update(listOf(raw()), nowMs = 0L)

        // 0.005 shift on every edge: below deadband, box must be bit-identical.
        val jittered = raw(left = 0.205f, top = 0.205f, right = 0.605f, bottom = 0.705f)
        val second = smoother.update(listOf(jittered), nowMs = 100L)

        assertEquals(1, second.size)
        assertEquals(first[0].left, second[0].left)
        assertEquals(first[0].top, second[0].top)
        assertEquals(first[0].right, second[0].right)
        assertEquals(first[0].bottom, second[0].bottom)
    }

    @Test
    fun largeMoveUpdatesBox() {
        val smoother = DetectionSmoother(deadbandFrac = 0.02f)
        smoother.update(listOf(raw()), nowMs = 0L)

        // 0.10 shift: still the same object (IoU ~0.43) but above the
        // deadband, so the held box must follow it.
        val moved = raw(left = 0.30f, top = 0.30f, right = 0.70f, bottom = 0.80f)
        val second = smoother.update(listOf(moved), nowMs = 100L)

        assertEquals(1, second.size)
        assertEquals(0.30f, second[0].left)
    }
}
