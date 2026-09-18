package com.agrelius.wasegmul

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.52 fixes.
 */
class MessageGeneratorTest {

    @Test
    fun humanizeLabel_preservesHyphensAndAcronyms() {
        assertEquals("Air-Conditioner", MessageGenerator.humanizeLabel("Air-Conditioner"))
        assertEquals("Air-Conditioner", MessageGenerator.humanizeLabel("air-conditioner"))
        assertEquals("PET", MessageGenerator.humanizeLabel("pet"))
        assertEquals("HDPE", MessageGenerator.humanizeLabel("HDPE"))
        assertEquals("PCB", MessageGenerator.humanizeLabel("pcb"))
        assertEquals("Disposable Plastic Cutlery", MessageGenerator.humanizeLabel("disposable_plastic_cutlery"))
        assertEquals("", MessageGenerator.humanizeLabel("   "))
    }

    @Test
    fun generateBarcode_blankProductName_throws() {
        assertFailsWith<IllegalArgumentException> {
            MessageGenerator.generateBarcode("", "Recyclable", "Plastic", ClassificationMode.BARCODE_GROUND_TRUTH)
        }
        assertFailsWith<IllegalArgumentException> {
            MessageGenerator.generateBarcode("   ", "Recyclable", "Plastic", ClassificationMode.BARCODE_PARTIAL)
        }
    }

    @Test
    fun generateBarcode_validName_mentionsAllParts() {
        val msg = MessageGenerator.generateBarcode(
            "Soda Can", "Recyclable", "metal", ClassificationMode.BARCODE_VISUAL_CONSENSUS, Random(1)
        )
        assertTrue(msg.contains("Soda Can"))
        assertTrue(msg.contains("Metal"))
        assertTrue(msg.contains("Recyclable"))
    }

    @Test
    fun subclassOnly_preservesCallerCategory() {
        // Caller (flat bridge) resolved Recyclable from tops; mapping re-derivation must
        // not override it. Use a subclass whose mapping DIFFERS from the caller category:
        // "can" maps to Recyclable, so use Organic caller + Organic subclass mapping...
        // Simplest direct case: caller category wins when it is a real taxonomy value.
        val msg = MessageGenerator.generate(
            category = "Recyclable", subcategory = "Plastic",
            catConfidence = 0f, subConfidence = 0.9f,
            topSubcategories = emptyList(), topCategories = emptyList(),
            mode = ClassificationMode.SUBCLASS_ONLY, random = Random(2)
        )
        assertTrue(msg.contains("Recyclable"), "caller category must be preserved, got: $msg")
    }

    @Test
    fun subclassOnly_unknownCallerFallsBackToMapping() {
        val msg = MessageGenerator.generate(
            category = "Unknown", subcategory = "Laptop",
            catConfidence = 0f, subConfidence = 0.9f,
            topSubcategories = emptyList(), topCategories = emptyList(),
            mode = ClassificationMode.SUBCLASS_ONLY, random = Random(3)
        )
        assertTrue(msg.contains("E-Waste"), "mapping fallback expected, got: $msg")
    }

    @Test
    fun randomParam_seededIsDeterministic() {
        fun msg(seed: Int) = MessageGenerator.generate(
            category = "Recyclable", subcategory = "Plastic",
            catConfidence = 0.9f, subConfidence = 0.85f,
            topSubcategories = emptyList(), topCategories = emptyList(),
            mode = ClassificationMode.CATEGORY_ONLY, random = Random(seed)
        )
        assertEquals(msg(42), msg(42))
    }

    @Test
    fun nanConfidence_rendersZeroPercentWithoutThrowing() {
        val msg = MessageGenerator.generate(
            category = "Recyclable", subcategory = "Plastic",
            catConfidence = 0.9f, subConfidence = Float.NaN,
            topSubcategories = listOf("Plastic" to Float.NaN),
            topCategories = emptyList(),
            mode = ClassificationMode.CATEGORY_OVERRIDE_MATCH, random = Random(4)
        )
        assertTrue(msg.isNotBlank())
        // NaN best-match conf must render as 0%, never "NaN%".
        assertTrue(!msg.contains("NaN"), "NaN leaked into message: $msg")
    }

    @Test
    fun unknownTarget_neverElectsBestMatch() {
        // UNKNOWN target with UNKNOWN-mapping tops must fall to the no-match pool,
        // not present garbage as "best".
        val msg = MessageGenerator.generate(
            category = "Unknown", subcategory = "AlienX",
            catConfidence = 0.9f, subConfidence = 0.9f,
            topSubcategories = listOf("AlienX" to 0.9f),
            topCategories = emptyList(),
            mode = ClassificationMode.CATEGORY_OVERRIDE_MATCH, random = Random(5)
        )
        assertTrue(!msg.contains("AlienX at") && !msg.contains("AlienX ("), "garbage best-match: $msg")
    }
}
