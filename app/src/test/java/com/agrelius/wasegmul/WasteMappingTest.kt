package com.agrelius.wasegmul

import org.junit.Assert.*
import org.junit.Test

class WasteMappingTest {

    private val all30Subclasses = listOf(
        "Air-Conditioner",
        "Battery",
        "Cardboard",
        "Electronic Component",
        "Electronic Device",
        "Glass",
        "Keyboard",
        "Laptop",
        "Metal",
        "Microwave",
        "Miscellaneous Trash",
        "Mobile",
        "Mouse",
        "Organic",
        "PCB",
        "Paper",
        "Plastic",
        "Player",
        "Printer",
        "Refrigerator",
        "Television",
        "Textile Trash",
        "Washing Machine",
        "automobile wastes",
        "clothing",
        "disposable_plastic_cutlery",
        "light bulbs",
        "shoes",
        "styrofoam_cups",
        "styrofoam_food_containers"
    )

    private val validCategories = setOf("E-Waste", "Recyclable", "Organic", "Trash")

    @Test
    fun testAll30Subclasses_arePresentInMapping() {
        assertEquals(30, all30Subclasses.size)
        all30Subclasses.forEach { subclass ->
            assertTrue("Subclass '$subclass' should be in MAPPING", WasteMapping.MAPPING.containsKey(subclass))
        }
    }

    @Test
    fun testAll30Subclasses_mapToValidCanonicalCategory() {
        all30Subclasses.forEach { subclass ->
            val cat = WasteMapping.getCategory(subclass)
            assertTrue("Subclass '$subclass' mapped to invalid category '$cat'", cat in validCategories)
        }
    }

    @Test
    fun testAll30Subclasses_havePositiveWeights() {
        all30Subclasses.forEach { subclass ->
            val weight = WasteMapping.getWeight(subclass)
            assertTrue("Subclass '$subclass' weight should be positive, was $weight", weight > 0.0)
        }
    }

    @Test
    fun testHazardousClassification() {
        assertTrue("Battery must be hazardous", WasteMapping.isHazardous("Battery"))
        assertTrue("automobile wastes must be hazardous", WasteMapping.isHazardous("automobile wastes"))
        assertTrue("light bulbs must be hazardous", WasteMapping.isHazardous("light bulbs"))

        assertFalse("Plastic must not be hazardous", WasteMapping.isHazardous("Plastic"))
        assertFalse("Paper must not be hazardous", WasteMapping.isHazardous("Paper"))
        assertFalse("Organic must not be hazardous", WasteMapping.isHazardous("Organic"))
    }

    @Test
    fun testUnmappedSubclass_fallsBackToUnknown() {
        assertEquals(WasteMapping.UNKNOWN, WasteMapping.getCategory("NonExistentItem123"))
        assertEquals(0.05, WasteMapping.getWeight("NonExistentItem123"), 0.001)
        assertFalse(WasteMapping.isHazardous("NonExistentItem123"))
    }
}
