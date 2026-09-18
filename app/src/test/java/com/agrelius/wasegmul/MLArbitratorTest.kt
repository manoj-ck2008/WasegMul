package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test

class MLArbitratorTest {

    @Test
    fun testDualAgreement_returnsAgreedResult() {
        val input = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.92f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.88f,
            topCategories = listOf("Recyclable" to 0.92f, "Trash" to 0.05f),
            topSubcategories = listOf("Plastic" to 0.88f, "Glass" to 0.08f)
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals("Recyclable", output.category)
        assertEquals("Plastic", output.subcategory)
        assertEquals(0.88f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testCategoryOverride_withMatchingPrediction_selectsMatchedSubclass() {
        // Category model predicts E-Waste, but subclass model predicted Plastic (Recyclable).
        // Subclass top list contains "Mobile" (E-Waste) with 0.60 confidence.
        val input = PredictionResult(
            category = "E-Waste",
            categoryConfidence = 0.95f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.70f,
            topCategories = listOf("E-Waste" to 0.95f, "Trash" to 0.03f),
            topSubcategories = listOf(
                "Plastic" to 0.70f,
                "Mobile" to 0.60f,
                "Glass" to 0.10f
            )
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals("E-Waste", output.category)
        assertEquals("Mobile", output.subcategory)
        assertEquals(0.60f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testCategoryOverride_withoutMatchingPrediction_fallsBackToCategory() {
        // Category model predicts Organic, but subclass model predicted Battery and Keyboard
        val input = PredictionResult(
            category = "Organic",
            categoryConfidence = 0.91f,
            subcategory = "Battery",
            subcategoryConfidence = 0.65f,
            topCategories = listOf("Organic" to 0.91f, "Trash" to 0.05f),
            topSubcategories = listOf(
                "Battery" to 0.65f,
                "Keyboard" to 0.20f
            )
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals("Organic", output.category)
        assertEquals(WasteMapping.UNCERTAIN, output.subcategory)
        assertEquals(0f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testDegraded_categoryOnly_preservesCategory() {
        val input = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.85f,
            subcategory = "Unknown",
            subcategoryConfidence = 0f,
            topCategories = listOf("Recyclable" to 0.85f),
            topSubcategories = emptyList()
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals("Recyclable", output.category)
        assertEquals(WasteMapping.UNCERTAIN, output.subcategory)
        assertEquals(0f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testDegraded_subclassOnly_derivesCategory() {
        val input = PredictionResult(
            category = "Unknown",
            categoryConfidence = 0f,
            subcategory = "Laptop",
            subcategoryConfidence = 0.89f,
            topCategories = emptyList(),
            topSubcategories = listOf("Laptop" to 0.89f)
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals("E-Waste", output.category)
        assertEquals("Laptop", output.subcategory)
        assertEquals(0.89f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testBothUncertain_returnsUncertainState() {
        val input = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.28f,
            subcategory = "Paper",
            subcategoryConfidence = 0.25f,
            topCategories = listOf(
                "Recyclable" to 0.28f,
                "Trash" to 0.26f,
                "Organic" to 0.24f,
                "E-Waste" to 0.22f
            ),
            topSubcategories = listOf(
                "Paper" to 0.25f,
                "Glass" to 0.24f
            )
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals(WasteMapping.UNCERTAIN, output.category)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testUnmappedSubclass_returnsUnknown() {
        val input = PredictionResult(
            category = "Unknown",
            categoryConfidence = 0f,
            subcategory = "AlienArtifactX",
            subcategoryConfidence = 0.90f,
            topCategories = emptyList(),
            topSubcategories = listOf("AlienArtifactX" to 0.90f)
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals(WasteMapping.UNKNOWN, output.category)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testComputeEntropy_preservesUnobservedMass() {
        val preds = listOf("A" to 0.4f, "B" to 0.2f)
        val entropy = MLArbitrator.computeEntropy(preds)
        assertTrue("Entropy should be positive and account for unobserved mass", entropy > 0f)
    }

    @Test
    fun testBothAgreeHigh_confidenceMode() {
        val input = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.95f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.90f,
            topCategories = listOf("Recyclable" to 0.95f),
            topSubcategories = listOf("Plastic" to 0.90f)
        )

        val output = MLArbitrator.arbitrate(input)
        assertEquals("Recyclable", output.category)
        assertEquals("Plastic", output.subcategory)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testConfusedCategory_highConfidenceSubclass_trustsSubclass() {        // Category model is confused (low confidence 0.35, high entropy), but subclass model is 0.95 confident for Laptop (E-Waste)
        val input = PredictionResult(
            category = "Trash",
            categoryConfidence = 0.35f,
            subcategory = "Laptop",
            subcategoryConfidence = 0.95f,
            topCategories = listOf(
                "Trash" to 0.35f,
                "Recyclable" to 0.30f,
                "Organic" to 0.20f,
                "E-Waste" to 0.15f
            ),
            topSubcategories = listOf(
                "Laptop" to 0.95f,
                "Mobile" to 0.03f
            )
        )

        val output = MLArbitrator.arbitrate(input)
        // Should trust the 95% confident subclass model and derive E-Waste
        assertEquals("E-Waste", output.category)
        assertEquals("Laptop", output.subcategory)
        assertEquals(0.95f, output.subcategoryConfidence, 0.001f)
        assertTrue(output.classificationMessage.isNotBlank())
    }

    @Test
    fun testNeither_zeroesStaleConfidencesAndPreservesRaw() {
        val input = PredictionResult(
            category = "Recyclable",
            categoryConfidence = 0.7f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.6f,
            topCategories = emptyList(),
            topSubcategories = emptyList()
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals(WasteMapping.UNCERTAIN, output.category)
        assertEquals(WasteMapping.UNCERTAIN, output.subcategory)
        assertEquals(0f, output.categoryConfidence, 0.001f)
        assertEquals(0f, output.subcategoryConfidence, 0.001f)
        assertEquals(0.7f, output.rawCategoryConfidence!!, 0.001f)
        assertEquals(0.6f, output.rawSubcategoryConfidence!!, 0.001f)
    }

    @Test
    fun testEntropy_emptyIsMaxUncertain() {
        assertEquals(2.0f, MLArbitrator.computeEntropy(emptyList()), 0.001f)
    }

    @Test
    fun testUnknownCategory_rejectedNotPassedThrough() {
        val input = PredictionResult(
            category = "FooBar",
            categoryConfidence = 0.99f,
            subcategory = "Plastic",
            subcategoryConfidence = 0.9f,
            topCategories = listOf("FooBar" to 0.99f),
            topSubcategories = listOf("Plastic" to 0.9f)
        )

        val output = MLArbitrator.arbitrate(input)

        assertEquals(WasteMapping.UNKNOWN, output.category)
        assertEquals(0f, output.categoryConfidence, 0.001f)
    }

    @Test
    fun testStrictSlider_forcesUncertainOverSubclassGuess() {
        val input = PredictionResult(
            category = "Trash",
            categoryConfidence = 0.3f,
            subcategory = "Laptop",
            subcategoryConfidence = 0.70f,
            topCategories = listOf("Trash" to 0.3f, "E-Waste" to 0.3f),
            topSubcategories = listOf("Laptop" to 0.70f)
        )

        val output = MLArbitrator.arbitrate(input, 0.95f)

        assertEquals(WasteMapping.UNCERTAIN, output.category)
    }
}
