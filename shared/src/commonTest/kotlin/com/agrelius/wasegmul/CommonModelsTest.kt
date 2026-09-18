package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.59 fixes.
 */
class CommonModelsTest {

    @Test
    fun confidencePolicy_rejectsNaNAndOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = 2.0f)
        }
        assertFailsWith<IllegalArgumentException> {
            WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            ClassificationResult("R", "P", Float.NaN, emptyList(), "d", "e", "r", "s")
        }
        assertFailsWith<IllegalArgumentException> {
            InternalResult("P", 5.0f)
        }
    }

    @Test
    fun timestamp_rejectsNegative_allowsLegacyZero() {
        assertFailsWith<IllegalArgumentException> {
            WasteRecord(category = "R", subclass = "P", confidence = 0.5f, timestamp = -1L)
        }
        WasteRecord(category = "R", subclass = "P", confidence = 0.5f, timestamp = 0L)
    }

    @Test
    fun vocabHelpers_recogniseClosedSets() {
        assertTrue(WasteSource.isKnown("camera"))
        assertTrue(WasteSource.isKnown("  VISUAL_ML "))
        assertFalse(WasteSource.isKnown("telepathy"))
        assertTrue(WasteFeedback.isCorrectMark(" Correct "))
        assertFalse(WasteFeedback.isCorrectMark("incorrect"))
    }

    @Test
    fun barcodeEvidence_failClosedByDefault() {
        val evidence = BarcodeEvidence(productName = "X", category = "Recyclable", subclass = "Glass")
        assertFalse(evidence.isComplete, "forgotten flags must not read as ground truth")
    }

    @Test
    fun topPredictionHelpers_roundTripViaCodec() {
        val record = WasteRecord(
            category = "Recyclable", subclass = "Plastic", confidence = 0.9f,
            topPredictions = WasteRecord.encodeTopPredictions(listOf("Plastic" to 0.9f, "Glass" to 0.1f))
        )
        assertEquals(listOf("Plastic" to 0.9f, "Glass" to 0.1f), record.decodedTopPredictions())
    }

    @Test
    fun predictionResult_sanitized_coercesWithoutThrowing() {
        val dirty = PredictionResult(
            category = "Recyclable", categoryConfidence = 5.0f,
            subcategory = "Plastic", subcategoryConfidence = Float.NaN,
            topSubcategories = listOf("Plastic" to 2.0f, " " to 0.5f, "Glass" to Float.NaN),
            topCategories = listOf("Recyclable" to 0.9f)
        )
        val clean = dirty.sanitized()
        assertEquals(1.0f, clean.categoryConfidence, 0.001f)
        assertEquals(0f, clean.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Plastic" to 1.0f), clean.topSubcategories)
    }
}
