package com.agrelius.wasegmul.ui.yolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red-phase tests for per-detection YOLO guidance.
 *
 * Spec: every live detection must surface Category + disposal action +
 * one-line handling tip, reusing WasteMapping/WasteKnowledgeBase (no new
 * science). Closed disposal-action vocabulary.
 */
class YoloGuidanceTest {

    @Test
    fun plasticMapsToRecyclableRecycleWithTip() {
        val guidance = guidanceForDetection("Plastic")

        assertEquals("Recyclable", guidance.category)
        assertEquals("Recycle", guidance.disposalAction)
        assertFalse(guidance.tip.isBlank())
        // One line only: no embedded newlines or bullet markers.
        assertFalse(guidance.tip.contains("\n"))
        assertFalse(guidance.tip.startsWith("•"))
    }

    @Test
    fun batteryMapsToEWasteDropOff() {
        val guidance = guidanceForDetection("Battery")

        assertEquals("E-Waste", guidance.category)
        assertEquals("Drop-off", guidance.disposalAction)
        assertFalse(guidance.tip.isBlank())
    }

    @Test
    fun organicMapsToCompost() {
        val guidance = guidanceForDetection("Organic")

        assertEquals("Organic", guidance.category)
        assertEquals("Compost", guidance.disposalAction)
    }

    @Test
    fun unknownLabelFallsBackToCheckAction() {
        val guidance = guidanceForDetection("definitely_not_a_real_label_xyz")

        assertEquals("Unknown", guidance.category)
        assertEquals("Check", guidance.disposalAction)
        assertFalse(guidance.tip.isBlank())
    }

    @Test
    fun disposalActionIsClosedVocabulary() {
        val allowed = setOf("Recycle", "Compost", "Discard", "Drop-off", "Check")
        listOf("Plastic", "Battery", "Organic", "Cardboard", "clothing", "Mobile").forEach { label ->
            assertTrue(
                "action for $label must be closed vocab",
                guidanceForDetection(label).disposalAction in allowed
            )
        }
    }
}
