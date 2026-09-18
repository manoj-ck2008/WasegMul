package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test

class MessageGeneratorTest {

    @Test
    fun testAllModes_generateNonEmptyMessage() {
        val topSubs = listOf("Plastic" to 0.85f, "Glass" to 0.10f)
        val topCats = listOf("Recyclable" to 0.90f, "Trash" to 0.08f)

        for (mode in ClassificationMode.values()) {
            val msg = MessageGenerator.generate(
                category = "Recyclable",
                subcategory = "Plastic",
                catConfidence = 0.90f,
                subConfidence = 0.85f,
                topSubcategories = topSubs,
                topCategories = topCats,
                mode = mode
            )
            assertTrue("Message for mode $mode should not be blank", msg.isNotBlank())
        }
    }

    @Test
    fun testHumanizeLabel() {
        assertEquals("Disposable Plastic Cutlery", MessageGenerator.humanizeLabel("disposable_plastic_cutlery"))
        assertEquals("Styrofoam Cups", MessageGenerator.humanizeLabel("styrofoam_cups"))
        assertEquals("Battery", MessageGenerator.humanizeLabel("battery"))
    }

    @Test
    fun testMessageAppliesHumanization() {
        val msg = MessageGenerator.generate(
            category = "Trash",
            subcategory = "disposable_plastic_cutlery",
            catConfidence = 0.90f,
            subConfidence = 0.85f,
            topSubcategories = emptyList(),
            topCategories = emptyList(),
            mode = ClassificationMode.BOTH_AGREE
        )
        assertTrue("Message should contain humanized label", msg.contains("Disposable Plastic Cutlery"))
        assertFalse("Message should not contain raw snake_case label", msg.contains("disposable_plastic_cutlery"))
    }

    @Test
    fun testGenerateBarcode_groundTruth() {
        val msg = MessageGenerator.generateBarcode(
            productName = "Organic Milk Bottle",
            category = "Recyclable",
            subclass = "plastic",
            mode = ClassificationMode.BARCODE_GROUND_TRUTH
        )
        assertTrue("Should mention product name", msg.contains("Organic Milk Bottle"))
        assertTrue("Should mention humanized subclass", msg.contains("Plastic"))
        assertTrue("Should mention category", msg.contains("Recyclable"))
    }

    @Test
    fun testGenerateBarcode_partial() {
        val msg = MessageGenerator.generateBarcode(
            productName = "Granola Bar",
            category = "Trash",
            subclass = "plastic",
            mode = ClassificationMode.BARCODE_PARTIAL
        )
        assertTrue("Should mention product name", msg.contains("Granola Bar"))
        assertTrue("Should mention humanized subclass", msg.contains("Plastic"))
        assertTrue("Should mention category", msg.contains("Trash"))
    }

    @Test
    fun testGenerateBarcode_visualConsensus() {
        val msg = MessageGenerator.generateBarcode(
            productName = "Soda Can",
            category = "Recyclable",
            subclass = "metal",
            mode = ClassificationMode.BARCODE_VISUAL_CONSENSUS
        )
        assertTrue("Should mention product name", msg.contains("Soda Can"))
        assertTrue("Should mention humanized subclass", msg.contains("Metal"))
        assertTrue("Should mention category", msg.contains("Recyclable"))
    }

    @Test
    fun testGenerateBarcode_blankProductNameThrows() {
        // Audit §3.52: the API never synthesises product names — UI renders its own fallback.
        try {
            MessageGenerator.generateBarcode(
                productName = "",
                category = "Recyclable",
                subclass = "cardboard",
                mode = ClassificationMode.BARCODE_GROUND_TRUTH
            )
            fail("blank productName must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("productName"))
        }
    }

    @Test
    fun testGenerateBarcode_fallbackToGenerateForNonBarcodeModes() {
        val msg = MessageGenerator.generateBarcode(
            productName = "Soda Can",
            category = "Recyclable",
            subclass = "metal",
            mode = ClassificationMode.BOTH_AGREE
        )
        assertTrue("Should generate agree message", msg.contains("Metal") && msg.contains("Recyclable"))
    }

    @Test
    fun testModerateAgreement_neverClaimsHighDependability() {
        val banned = listOf("highly dependable", "High-confidence", "High reliability")
        for (seed in 0..20) {
            val msg = MessageGenerator.generate(
                category = "Recyclable",
                subcategory = "Plastic",
                catConfidence = 0.55f,
                subConfidence = 0.55f,
                topSubcategories = listOf("Plastic" to 0.55f),
                topCategories = listOf("Recyclable" to 0.55f),
                mode = ClassificationMode.BOTH_AGREE,
                random = kotlin.random.Random(seed)
            )
            banned.forEach { phrase ->
                assertFalse("seed $seed leaked '$phrase'", msg.contains(phrase))
            }
        }
    }

    @Test
    fun testHumanizeLabel_preservesHyphensAndAcronyms() {
        assertEquals("Air-Conditioner", MessageGenerator.humanizeLabel("Air-Conditioner"))
        assertEquals("PET", MessageGenerator.humanizeLabel("pet"))
        assertEquals("HDPE", MessageGenerator.humanizeLabel("HDPE"))
    }
}
