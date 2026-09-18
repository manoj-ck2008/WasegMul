package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.53 fixes.
 */
class WasteMappingTest {

    @Test
    fun liIonDevices_flaggedHazardous() {
        for (label in listOf("Mobile", "Laptop", "Player", "Electronic Device", "cell phone", "electronic")) {
            assertTrue(WasteMapping.isHazardous(label), "$label carries a Li-ion cell and must be hazardous")
        }
    }

    @Test
    fun hazardConsistency_documentedSplit() {
        assertTrue(WasteMapping.isHazardous("Microwave"), "capacitor retains lethal charge")
        assertTrue(WasteMapping.isHazardous("Printer"), "toner + lead solder")
        assertFalse(WasteMapping.isHazardous("Washing Machine"), "bulk steel, no cell/capacitor/refrigerant")
        assertFalse(WasteMapping.isHazardous("Plastic"))
    }

    @Test
    fun curbsideTruth_filmDrinkwareTextilesAreTrash() {
        assertEquals("Trash", WasteMapping.getCategory("plastic_bag"))
        assertEquals("Trash", WasteMapping.getCategory("plastic_wrapper"))
        assertEquals("Trash", WasteMapping.getCategory("wine glass"))
        assertEquals("Trash", WasteMapping.getCategory("clothing"))
        assertEquals("Trash", WasteMapping.getCategory("textile"))
        // Identical fibres earn identical categories (no opposite credit).
        assertEquals(WasteMapping.getCategory("textile"), WasteMapping.getCategory("clothing"))
    }

    @Test
    fun knownCategories_acceptResidualAlias_rejectUnknown() {
        assertTrue(WasteMapping.isKnownCategory("Recyclable"))
        assertTrue(WasteMapping.isKnownCategory("Residual"), "CONTEXT.md alias must validate")
        assertTrue(WasteMapping.isKnownCategory("  trash  "), "trimmed + case-insensitive")
        assertFalse(WasteMapping.isKnownCategory("FooBar"))
        assertTrue(WasteMapping.isSentinel("Uncertain"))
        assertTrue(WasteMapping.isSentinel("Unknown"))
        assertFalse(WasteMapping.isSentinel("Recyclable"))
    }

    @Test
    fun failClosedLookup_returnsNullForUnmapped() {
        assertNull(WasteMapping.getMetaOrNull("NonExistentItem123"))
        assertEquals(WasteMapping.UNKNOWN, WasteMapping.getCategory("NonExistentItem123"))
        assertFalse(WasteMapping.isHazardous("NonExistentItem123"))
    }

    @Test
    fun singleDefaultWeight_sharedWithRecords() {
        // ONE 0.05 source: unmapped lookup == fresh record default.
        assertEquals(WasteMapping.DEFAULT_WEIGHT_KG, WasteMapping.getWeight("NonExistentItem123"), 0.0)
        assertEquals(
            WasteMapping.DEFAULT_WEIGHT_KG,
            WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = 0.9f).estimatedWeight,
            0.0
        )
    }

    @Test
    fun canonicalKey_caseInsensitive() {
        assertEquals("Mobile", WasteMapping.canonicalKey("mobile"))
        assertEquals("plastic_bag", WasteMapping.canonicalKey("  PLASTIC_BAG "))
        assertNull(WasteMapping.canonicalKey("nope"))
    }

    @Test
    fun automobileWeight_tyreScaleNotCarScale() {
        val w = WasteMapping.getWeight("automobile wastes")
        assertTrue(w >= 8.0 && w <= 20.0, "tyre-equivalent unit expected, was $w")
        assertNotNull(WasteMapping.getMetaOrNull("automobile wastes"))
    }
}
