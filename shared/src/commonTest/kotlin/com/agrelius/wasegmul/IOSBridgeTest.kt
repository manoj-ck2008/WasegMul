package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.57 fixes.
 */
class IOSBridgeTest {

    @Test
    fun flatArbitration_rejectsUnequalSizes() {
        assertFailsWith<IllegalArgumentException> {
            IOSBridge.arbitrateMLFlat(
                category = "Recyclable", categoryConfidence = 0.9f,
                subclass = "Plastic", subclassConfidence = 0.8f,
                topSubclassLabels = listOf("Plastic", "Glass"),
                topSubclassConfidences = listOf(0.8f)
            )
        }
    }

    @Test
    fun flatArbitration_acceptsLowThreshold() {
        val out = IOSBridge.arbitrateMLFlat(
            category = "Recyclable", categoryConfidence = 0.3f,
            subclass = "Plastic", subclassConfidence = 0.3f,
            topSubclassLabels = listOf("Plastic"),
            topSubclassConfidences = listOf(0.3f),
            topCategoryLabels = listOf("Recyclable"),
            topCategoryConfidences = listOf(0.3f),
            lowThreshold = 0.95f
        )
        assertEquals(WasteMapping.UNCERTAIN, out.category)
    }

    @Test
    fun strictMode_unknownThrows_legacyFallsBack() {
        assertFailsWith<IllegalArgumentException> {
            IOSBridge.generateMessageStrict("Recyclable", "Plastic", 0.9f, 0.8f, "TYPO_MODE")
        }
        val legacy = IOSBridge.generateMessage("Recyclable", "Plastic", 0.9f, 0.8f, "TYPO_MODE")
        assertTrue(legacy.isNotBlank())
    }

    @Test
    fun swiftFriendlyDecode_noReparseNeeded() {
        val raw = IOSBridge.encodePredictions(listOf("Plastic", "Glass"), listOf(0.9f, 0.1f))
        assertEquals(listOf("Plastic", "Glass"), IOSBridge.decodePredictionLabels(raw))
        assertEquals(listOf(0.9f, 0.1f), IOSBridge.decodePredictionConfidences(raw))
        assertEquals(mapOf("Plastic" to 0.9f, "Glass" to 0.1f), IOSBridge.decodePredictionsMap(raw))
    }

    @Test
    fun encodePredictions_rejectsUnequalSizes() {
        assertFailsWith<IllegalArgumentException> {
            IOSBridge.encodePredictions(listOf("A"), listOf(0.5f, 0.5f))
        }
    }

    @Test
    fun barcodeBridge_consensusMentionsProduct() {
        val out = IOSBridge.arbitrateMLBarcode(
            category = "Recyclable", categoryConfidence = 0.9f,
            subclass = "Plastic", subclassConfidence = 0.85f,
            topSubclassLabels = listOf("Plastic"), topSubclassConfidences = listOf(0.85f),
            topCategoryLabels = listOf("Recyclable"), topCategoryConfidences = listOf(0.9f),
            productName = "Test Water", barcodeCategory = "Recyclable",
            barcodeSubclass = "Plastic", isComplete = true
        )
        assertEquals("Recyclable", out.category)
        assertEquals(0.99f, out.categoryConfidence, 0.001f)
        assertTrue(out.classificationMessage.contains("Test Water"))
    }

    @Test
    fun barcodeMessage_blankNameThrows() {
        assertFailsWith<IllegalArgumentException> {
            IOSBridge.generateBarcodeMessage("", "Recyclable", "Plastic", "BARCODE_GROUND_TRUTH")
        }
        val ok = IOSBridge.generateBarcodeMessage("Oats", "Recyclable", "Cardboard", "BARCODE_GROUND_TRUTH")
        assertTrue(ok.contains("Oats"))
    }

    @Test
    fun flatEcoImpact_matchesRecordPath() {
        val flat = IOSBridge.calculateEcoImpactFlat(
            listOf("Recyclable"), listOf("Plastic"), listOf(1.0), listOf(0.9f)
        )
        assertEquals(1.8, flat.co2PreventedKg, 0.001)
        assertFailsWith<IllegalArgumentException> {
            IOSBridge.calculateEcoImpactFlatStrict(
                listOf("Recyclable"), listOf("Plastic", "Glass"), listOf(1.0), listOf(0.9f)
            )
        }
    }
}
