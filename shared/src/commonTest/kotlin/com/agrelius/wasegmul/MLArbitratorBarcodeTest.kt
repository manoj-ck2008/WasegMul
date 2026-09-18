package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MLArbitratorBarcodeTest {

    @Test
    fun testBarcodeOnly_complete_returnsBarcodeGroundTruthWithHighConfidence() {
        val barcode = BarcodeEvidence(
            productName = "Nutella Hazelnut Spread",
            category = "Recyclable",
            subclass = "Glass",
            isComplete = true
        )

        // Case A: prediction is null
        val resultFromNull = MLArbitrator.arbitrateWithBarcode(null, barcode)

        assertEquals("Recyclable", resultFromNull.category)
        assertEquals(0.98f, resultFromNull.categoryConfidence, 0.001f)
        assertEquals("Glass", resultFromNull.subcategory)
        assertEquals(0.98f, resultFromNull.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Glass" to 0.98f), resultFromNull.topSubcategories)
        assertEquals(listOf("Recyclable" to 0.98f), resultFromNull.topCategories)
        assertTrue(
            resultFromNull.classificationMessage.contains("Nutella Hazelnut Spread"),
            "Message should reference product name"
        )

        // Case B: prediction is non-null but has empty top categories and top subcategories
        val emptyPrediction = PredictionResult(
            category = "",
            categoryConfidence = 0f,
            subcategory = "",
            subcategoryConfidence = 0f,
            topCategories = emptyList(),
            topSubcategories = emptyList()
        )
        val resultFromEmpty = MLArbitrator.arbitrateWithBarcode(emptyPrediction, barcode)

        assertEquals("Recyclable", resultFromEmpty.category)
        assertEquals(0.98f, resultFromEmpty.categoryConfidence, 0.001f)
        assertEquals("Glass", resultFromEmpty.subcategory)
        assertEquals(0.98f, resultFromEmpty.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Glass" to 0.98f), resultFromEmpty.topSubcategories)
        assertEquals(listOf("Recyclable" to 0.98f), resultFromEmpty.topCategories)
        assertTrue(
            resultFromEmpty.classificationMessage.contains("Nutella Hazelnut Spread"),
            "Message should reference product name"
        )
    }

    @Test
    fun testBarcodeOnly_incomplete_returnsBarcodePartialWithModerateConfidence() {
        val barcode = BarcodeEvidence(
            productName = "Generic Soda Can",
            category = "Recyclable",
            subclass = "Metal",
            isComplete = false
        )

        // Case A: prediction is null
        val resultFromNull = MLArbitrator.arbitrateWithBarcode(null, barcode)

        assertEquals("Recyclable", resultFromNull.category)
        assertEquals(0.80f, resultFromNull.categoryConfidence, 0.001f)
        assertEquals("Metal", resultFromNull.subcategory)
        assertEquals(0.80f, resultFromNull.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Metal" to 0.80f), resultFromNull.topSubcategories)
        assertEquals(listOf("Recyclable" to 0.80f), resultFromNull.topCategories)
        assertTrue(
            resultFromNull.classificationMessage.contains("Generic Soda Can"),
            "Message should reference product name"
        )

        // Case B: prediction is non-null but has empty top lists
        val emptyPrediction = PredictionResult(
            category = "Unknown",
            categoryConfidence = 0f,
            subcategory = "Unknown",
            subcategoryConfidence = 0f,
            topCategories = emptyList(),
            topSubcategories = emptyList()
        )
        val resultFromEmpty = MLArbitrator.arbitrateWithBarcode(emptyPrediction, barcode)

        assertEquals("Recyclable", resultFromEmpty.category)
        assertEquals(0.80f, resultFromEmpty.categoryConfidence, 0.001f)
        assertEquals("Metal", resultFromEmpty.subcategory)
        assertEquals(0.80f, resultFromEmpty.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Metal" to 0.80f), resultFromEmpty.topSubcategories)
        assertEquals(listOf("Recyclable" to 0.80f), resultFromEmpty.topCategories)
        assertTrue(
            resultFromEmpty.classificationMessage.contains("Generic Soda Can"),
            "Message should reference product name"
        )
    }

    @Test
    fun testBarcodeVisualConsensus_returnsConsensusWithHighestConfidenceAndMergedTopLists() {
        val visualPrediction = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.91f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.86f,
            topCategories = listOf(
                "Recyclable" to 0.91f,
                "Trash" to 0.06f,
                "Organic" to 0.03f
            ),
            topSubcategories = listOf(
                "Plastic" to 0.86f,
                "Glass" to 0.09f,
                "Metal" to 0.05f
            )
        )

        val barcode = BarcodeEvidence(
            productName = "Evian Natural Mineral Water",
            category = "Recyclable",
            subclass = "Plastic",
            isComplete = true
        )

        val result = MLArbitrator.arbitrateWithBarcode(visualPrediction, barcode)

        // Category & subclass from barcode
        assertEquals("Recyclable", result.category)
        assertEquals("Plastic", result.subcategory)

        // Consensus confidence is 0.99f
        assertEquals(0.99f, result.categoryConfidence, 0.001f)
        assertEquals(0.99f, result.subcategoryConfidence, 0.001f)

        // Merged topCategories: barcode category at top with 0.99f, other categories preserved
        assertEquals(3, result.topCategories.size)
        assertEquals("Recyclable" to 0.99f, result.topCategories[0])
        assertEquals("Trash" to 0.06f, result.topCategories[1])
        assertEquals("Organic" to 0.03f, result.topCategories[2])

        // Merged topSubcategories: barcode subclass at top with 0.99f, other subcategories preserved
        assertEquals(3, result.topSubcategories.size)
        assertEquals("Plastic" to 0.99f, result.topSubcategories[0])
        assertEquals("Glass" to 0.09f, result.topSubcategories[1])
        assertEquals("Metal" to 0.05f, result.topSubcategories[2])

        // Message should reference product name
        assertTrue(
            result.classificationMessage.contains("Evian Natural Mineral Water"),
            "Message should reference product name"
        )
    }

    @Test
    fun testBarcodeVisualConsensus_caseInsensitiveCategoryMatch() {
        val visualPrediction = PredictionResult(
            category = "recyclable",
            categoryConfidence = 0.85f,
            subcategory = "cardboard",
            subcategoryConfidence = 0.82f,
            topCategories = listOf("recyclable" to 0.85f, "trash" to 0.15f),
            topSubcategories = listOf("cardboard" to 0.82f, "paper" to 0.12f)
        )

        val barcode = BarcodeEvidence(
            productName = "Cereal Box",
            category = "Recyclable",
            subclass = "Cardboard",
            isComplete = true
        )

        val result = MLArbitrator.arbitrateWithBarcode(visualPrediction, barcode)

        assertEquals("Recyclable", result.category)
        assertEquals("Cardboard", result.subcategory)
        assertEquals(0.99f, result.categoryConfidence, 0.001f)
        assertEquals(0.99f, result.subcategoryConfidence, 0.001f)
        assertEquals("Recyclable" to 0.99f, result.topCategories.first())
        assertEquals("Cardboard" to 0.99f, result.topSubcategories.first())
    }

    @Test
    fun testBarcodeVisualConsensus_differentSubclassInSameCategory() {
        // Visual model saw Paper (0.80), but barcode specifically identifies Cardboard
        val visualPrediction = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.90f,
            subcategory = "Paper",
            subcategoryConfidence = 0.80f,
            topCategories = listOf("Recyclable" to 0.90f),
            topSubcategories = listOf("Paper" to 0.80f, "Plastic" to 0.10f)
        )

        val barcode = BarcodeEvidence(
            productName = "Shipping Carton",
            category = "Recyclable",
            subclass = "Cardboard",
            isComplete = true
        )

        val result = MLArbitrator.arbitrateWithBarcode(visualPrediction, barcode)

        assertEquals("Recyclable", result.category)
        assertEquals("Cardboard", result.subcategory)
        assertEquals(0.99f, result.categoryConfidence, 0.001f)
        assertEquals(0.99f, result.subcategoryConfidence, 0.001f)

        // Merged subcategories: Cardboard first at 0.99f, then Paper and Plastic
        assertEquals("Cardboard" to 0.99f, result.topSubcategories[0])
        assertEquals("Paper" to 0.80f, result.topSubcategories[1])
        assertEquals("Plastic" to 0.10f, result.topSubcategories[2])
    }

    @Test
    fun testBarcodeVisualConflict_barcodeOverridesVisualModel() {
        // Visual model confidently thinks this is Trash / Miscellaneous Trash
        val visualPrediction = PredictionResult(
            category = "Trash",
            categoryConfidence = 0.88f,
            subcategory = "Miscellaneous Trash",
            subcategoryConfidence = 0.84f,
            topCategories = listOf("Trash" to 0.88f, "Recyclable" to 0.10f),
            topSubcategories = listOf("Miscellaneous Trash" to 0.84f, "Plastic" to 0.12f)
        )

        // Barcode evidence indicates this is actually a recyclable metal can
        val barcode = BarcodeEvidence(
            productName = "Coca-Cola Original 330ml",
            category = "Recyclable",
            subclass = "Metal",
            isComplete = true
        )

        val result = MLArbitrator.arbitrateWithBarcode(visualPrediction, barcode)

        // Barcode ground truth overrides visual model
        assertEquals("Recyclable", result.category)
        assertEquals("Metal", result.subcategory)
        assertEquals(0.95f, result.categoryConfidence, 0.001f)
        assertEquals(0.95f, result.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Recyclable" to 0.95f), result.topCategories)
        assertEquals(listOf("Metal" to 0.95f), result.topSubcategories)
        assertTrue(
            result.classificationMessage.contains("Coca-Cola Original 330ml"),
            "Message should reference product name"
        )
    }

    @Test
    fun testBarcodeVisualConflict_organicVsRecyclable() {
        // Visual model confused apple core with cardboard
        val visualPrediction = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.75f,
            subcategory = "Cardboard",
            subcategoryConfidence = 0.70f,
            topCategories = listOf("Recyclable" to 0.75f, "Organic" to 0.20f),
            topSubcategories = listOf("Cardboard" to 0.70f)
        )

        val barcode = BarcodeEvidence(
            productName = "Fresh Organic Apple",
            category = "Organic",
            subclass = "Organic",
            isComplete = true
        )

        val result = MLArbitrator.arbitrateWithBarcode(visualPrediction, barcode)

        assertEquals("Organic", result.category)
        assertEquals("Organic", result.subcategory)
        assertEquals(0.95f, result.categoryConfidence, 0.001f)
        assertEquals(0.95f, result.subcategoryConfidence, 0.001f)
        assertEquals(listOf("Organic" to 0.95f), result.topCategories)
        assertEquals(listOf("Organic" to 0.95f), result.topSubcategories)
        assertTrue(
            result.classificationMessage.contains("Fresh Organic Apple"),
            "Message should reference product name"
        )
    }
}
