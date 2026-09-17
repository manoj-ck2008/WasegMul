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
}
