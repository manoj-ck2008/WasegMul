package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.54 fixes.
 */
class WasteKnowledgeBaseTest {

    @Test
    fun extendedLabels_canonicalisedNotGeneric() {
        // Previously "Bottle" (capitalised) missed the MAPPING-only canonicalisation and
        // fell to the generic else. It must now hit the specific Plastic branch.
        val lower = WasteKnowledgeBase.getInfo("Recyclable", "bottle")
        val upper = WasteKnowledgeBase.getInfo("Recyclable", "Bottle")
        assertEquals(lower.disposalGuide, upper.disposalGuide)
        assertTrue(upper.disposalGuide.contains("RINSE"), "expected specific Plastic guidance, got: ${upper.disposalGuide}")
        assertFalse(upper.disposalGuide.contains("municipal guidelines"), "must not be generic fallback")
    }

    @Test
    fun hazardousAndResidual_haveCategoryGuidance() {
        val hz = WasteKnowledgeBase.getInfo("Hazardous", "Uncertain")
        assertTrue(hz.disposalGuide.contains("CATEGORY-LEVEL"))
        assertTrue(hz.disposalGuide.contains("HAZARDOUS"))
        val residual = WasteKnowledgeBase.getInfo("Residual", "Unknown")
        assertTrue(residual.disposalGuide.contains("CATEGORY-LEVEL"))
    }

    @Test
    fun metalStat_correctDirectionWithCitation() {
        val metal = WasteKnowledgeBase.getInfo("Recyclable", "Metal")
        // Inverted stat ("mining 95% more intensive", ~10x understatement) is gone;
        // correct direction: recycling SAVES ~95% (primary uses ~20x more).
        assertTrue(metal.environmentalImpact.contains("95%"), "stat missing: ${metal.environmentalImpact}")
        assertTrue(metal.environmentalImpact.contains("20"), "magnitude missing: ${metal.environmentalImpact}")
        assertTrue(metal.environmentalImpact.contains("saves"), "direction must be savings: ${metal.environmentalImpact}")
    }

    @Test
    fun tvMercury_ccflOnly() {
        val tv = WasteKnowledgeBase.getInfo("E-Waste", "Television")
        assertTrue(tv.disposalGuide.contains("CCFL"), "must scope mercury to CCFL units: ${tv.disposalGuide}")
        assertTrue(tv.disposalGuide.contains("LED"), "must exempt LED/OLED: ${tv.disposalGuide}")
    }

    @Test
    fun phonesAndLaptops_carryLiIonFireHandling() {
        val mobile = WasteKnowledgeBase.getInfo("E-Waste", "Mobile")
        assertTrue(
            mobile.disposalGuide.contains("lithium", ignoreCase = true) ||
                mobile.disposalGuide.contains("FIRE", ignoreCase = true),
            "Li-ion fire handling missing: ${mobile.disposalGuide}"
        )
    }

    @Test
    fun plasticBranch_excludesPvcAndPs() {
        val plastic = WasteKnowledgeBase.getInfo("Recyclable", "Plastic")
        assertTrue(plastic.disposalGuide.contains("#3 (PVC)"), "PVC exclusion missing")
        assertTrue(plastic.disposalGuide.contains("#6 (PS)"), "PS exclusion missing")
    }

    @Test
    fun wineGlass_isTrashGuidance() {
        val wg = WasteKnowledgeBase.getInfo("Trash", "wine glass")
        assertTrue(wg.disposalGuide.contains("TRASH"), "drinkware must bin as trash: ${wg.disposalGuide}")
    }

    @Test
    fun noSelfCitation_anywhere() {
        for (key in WasteMapping.MAPPING.keys + WasteMapping.EXTENDED_MAPPING.keys) {
            val info = WasteKnowledgeBase.getInfo(WasteMapping.getCategory(key), key)
            assertFalse(
                info.sources.contains("agrelius", ignoreCase = true),
                "$key still self-cites: ${info.sources}"
            )
            assertTrue(info.sources.isNotBlank(), "$key has blank sources")
        }
    }

    @Test
    fun canonicalLabel_coversBothMaps() {
        assertEquals("bottle", WasteKnowledgeBase.canonicalLabel("  BOTTLE "))
        assertEquals("Battery", WasteKnowledgeBase.canonicalLabel("battery"))
    }
}
