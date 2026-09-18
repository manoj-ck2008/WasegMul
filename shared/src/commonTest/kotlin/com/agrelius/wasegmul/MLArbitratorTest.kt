package com.agrelius.wasegmul

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.50 fixes. Expected values come from the spec
 * (log2(4) = 2.0 ceiling, documented thresholds), never recomputed from the impl.
 */
class MLArbitratorTest {

    private fun full(
        cat: String = "Recyclable",
        catConf: Float = 0.9f,
        sub: String = "Plastic",
        subConf: Float = 0.85f,
        tops: List<Pair<String, Float>> = listOf("Plastic" to 0.85f),
        topCats: List<Pair<String, Float>> = listOf("Recyclable" to 0.9f)
    ) = PredictionResult(
        category = cat, categoryConfidence = catConf,
        subcategory = sub, subcategoryConfidence = subConf,
        topSubcategories = tops, topCategories = topCats
    )

    @Test
    fun arbitrateNeither_zeroesConfidencesAndPreservesRaw() {
        val input = PredictionResult(
            category = "Recyclable", categoryConfidence = 0.7f,
            subcategory = "Plastic", subcategoryConfidence = 0.6f,
            topSubcategories = emptyList(), topCategories = emptyList()
        )
        val out = MLArbitrator.arbitrate(input)
        assertEquals(WasteMapping.UNCERTAIN, out.category)
        assertEquals(WasteMapping.UNCERTAIN, out.subcategory)
        assertEquals(0f, out.categoryConfidence)
        assertEquals(0f, out.subcategoryConfidence)
        assertEquals(0.7f, out.rawCategoryConfidence!!, 0.001f)
        assertEquals(0.6f, out.rawSubcategoryConfidence!!, 0.001f)
    }

    @Test
    fun computeEntropy_emptyIsMaxUncertainNotZero() {
        assertEquals(2.0f, MLArbitrator.computeEntropy(emptyList()), 0.001f)
        assertEquals(MLArbitrator.CATEGORY_ENTROPY_CEILING, MLArbitrator.computeEntropy(emptyList()), 0.0001f)
    }

    @Test
    fun computeEntropy_ignoresNaNAndNegatives() {
        val clean = MLArbitrator.computeEntropy(listOf("A" to 0.5f, "B" to 0.5f))
        val dirty = MLArbitrator.computeEntropy(
            listOf("A" to 0.5f, "B" to 0.5f, "X" to Float.NaN, "Y" to -2f, "Z" to Float.POSITIVE_INFINITY)
        )
        assertTrue(abs(clean - dirty) < 0.001f, "NaN/negative/infinite entries must not move entropy")
        // Uniform 2-class = exactly 1 bit (independent truth).
        assertEquals(1.0f, clean, 0.001f)
    }

    @Test
    fun subclassBar_tracksStrictLowThreshold() {
        // Subclass 0.70 clears the 0.65 floor but NOT a strict 0.95 slider:
        // must yield UNCERTAIN, not a forced subclass verdict.
        val input = PredictionResult(
            category = "Trash", categoryConfidence = 0.3f,
            subcategory = "Laptop", subcategoryConfidence = 0.70f,
            topCategories = listOf("Trash" to 0.3f, "E-Waste" to 0.3f),
            topSubcategories = listOf("Laptop" to 0.70f)
        )
        val strict = MLArbitrator.arbitrate(input, 0.95f)
        assertEquals(WasteMapping.UNCERTAIN, strict.category)
        // Same input under the default slider trusts the subclass evidence.
        val lax = MLArbitrator.arbitrate(input, 0.50f)
        assertEquals("E-Waste", lax.category)
        assertEquals("Laptop", lax.subcategory)
    }

    @Test
    fun lowThresholdNaN_fallsBackToDefault() {
        val input = PredictionResult(
            category = "Recyclable", categoryConfidence = 0.28f,
            subcategory = "Paper", subcategoryConfidence = 0.25f,
            topCategories = listOf("Recyclable" to 0.28f, "Trash" to 0.26f),
            topSubcategories = listOf("Paper" to 0.25f)
        )
        val out = MLArbitrator.arbitrate(input, Float.NaN)
        assertEquals(WasteMapping.UNCERTAIN, out.category)
    }

    @Test
    fun unknownCategoryTaxonomy_rejectedAsUnknown() {
        val out = MLArbitrator.arbitrate(full(cat = "FooBar", catConf = 0.99f))
        assertEquals(WasteMapping.UNKNOWN, out.category)
        assertEquals(0f, out.categoryConfidence)
        assertEquals(0.99f, out.rawCategoryConfidence!!, 0.001f)
    }

    @Test
    fun nanInput_sanitizedToUncertainNeverThrows() {
        val input = PredictionResult(
            category = "Recyclable", categoryConfidence = Float.NaN,
            subcategory = "Plastic", subcategoryConfidence = Float.NaN,
            topCategories = listOf("Recyclable" to Float.NaN),
            topSubcategories = listOf("Plastic" to Float.NaN)
        )
        val out = MLArbitrator.arbitrate(input)
        assertTrue(out.categoryConfidence.isFinite() && out.subcategoryConfidence.isFinite())
        assertEquals(WasteMapping.UNCERTAIN, out.category)
    }

    @Test
    fun highThreshold_boundaryDocumented() {
        // Spec: HIGH requires BOTH >= 0.80. The boundary itself is a spec constant.
        assertEquals(0.80f, MLArbitrator.BOTH_AGREE_HIGH_THRESHOLD, 0.0001f)
        // 4-class ceiling is log2(4) = 2.0 by construction, not a magic number.
        assertEquals(2.0f, MLArbitrator.CATEGORY_ENTROPY_CEILING, 0.0001f)
    }

    @Test
    fun moderateAgreement_neverClaimsHighDependability() {
        val banned = listOf("highly dependable", "High-confidence", "High reliability")
        for (seed in 0..20) {
            val msg = MessageGenerator.generate(
                category = "Recyclable", subcategory = "Plastic",
                catConfidence = 0.55f, subConfidence = 0.55f,
                topSubcategories = listOf("Plastic" to 0.55f),
                topCategories = listOf("Recyclable" to 0.55f),
                mode = ClassificationMode.BOTH_AGREE,
                random = kotlin.random.Random(seed)
            )
            for (phrase in banned) {
                assertTrue(!msg.contains(phrase), "seed $seed leaked '$phrase' into moderate agreement")
            }
        }
    }

    @Test
    fun mergeTopList_capsAndDocumentsEvidenceSemantics() {
        val big = (1..15).map { "label$it" to (0.9f - it * 0.01f) }
        val merged = MLArbitrator.mergeTopList("Primary", 0.99f, big)
        assertEquals(MLArbitrator.MAX_MERGED_TOPS, merged.size)
        assertEquals("Primary" to 0.99f, merged.first())
        // Sorted evidence, NaN dropped.
        val withNaN = MLArbitrator.mergeTopList("P", 0.5f, listOf("A" to Float.NaN, "B" to 0.2f))
        assertEquals(listOf("P" to 0.5f, "B" to 0.2f), withNaN)
    }

    @Test
    fun unmappedSubclass_zeroesWithRawPreserved() {
        val out = MLArbitrator.arbitrate(
            full(cat = "Unknown", catConf = 0f, sub = "AlienX", subConf = 0.9f, tops = listOf("AlienX" to 0.9f), topCats = emptyList())
        )
        assertEquals(WasteMapping.UNKNOWN, out.category)
        assertEquals(0f, out.categoryConfidence)
        assertEquals(0f, out.subcategoryConfidence)
        assertNull(out.rawCategoryConfidence?.takeIf { it != 0f })
    }
}
