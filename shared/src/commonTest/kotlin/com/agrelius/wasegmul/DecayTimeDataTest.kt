package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecayTimeDataTest {

    @Test
    fun testKnownSubclassesLookup() {
        val glass = DecayTimeData.getDecayInfo("Glass", "Recyclable")
        assertEquals("Glass", glass.subclass)
        assertTrue(glass.minYears >= 1000000.0)
        assertEquals(1, glass.severityLevel)

        val battery = DecayTimeData.getDecayInfo("Battery", "E-Waste")
        assertEquals("Battery", battery.subclass)
        assertTrue(battery.minYears >= 100.0)
        assertEquals(3, battery.severityLevel)

        val cardboard = DecayTimeData.getDecayInfo("Cardboard", "Recyclable")
        assertEquals("Cardboard", cardboard.subclass)
        assertTrue(cardboard.minYears < 1.0)
    }

    @Test
    fun testCaseInsensitiveSubclassLookup() {
        val plasticLower = DecayTimeData.getDecayInfo("plastic", "Recyclable")
        assertEquals("Plastic", plasticLower.subclass)
        assertTrue(plasticLower.minYears >= 100.0)
    }

    @Test
    fun testCategoryFallbacks() {
        val unknownEWaste = DecayTimeData.getDecayInfo("UnknownGadget", "E-Waste")
        assertTrue(unknownEWaste.minYears >= 100.0)
        assertEquals(3, unknownEWaste.severityLevel)

        val unknownOrganic = DecayTimeData.getDecayInfo("UnknownPeel", "Organic")
        assertTrue(unknownOrganic.minYears < 1.0)
        assertEquals(0, unknownOrganic.severityLevel)
    }

    @Test
    fun testComparisons() {
        // Anchors: season ~= 0.25 y, generation ~= 25 y (UN demographic convention).
        assertEquals("Less than a single season", DecayTimeData.getComparison(0.1))
        assertEquals("Less than a single season", DecayTimeData.getComparison(0.24))
        assertEquals("Within a single generation", DecayTimeData.getComparison(0.25))
        assertEquals("Within a single generation", DecayTimeData.getComparison(0.5))
        assertEquals("Within a single generation", DecayTimeData.getComparison(24.9))
        assertEquals("Your grandchildren would still see it", DecayTimeData.getComparison(25.0))
        assertEquals("Your grandchildren would still see it", DecayTimeData.getComparison(75.0))
        assertEquals("Outlasts every building standing today", DecayTimeData.getComparison(200.0))
        assertEquals("Longer than most civilizations have existed", DecayTimeData.getComparison(800.0))
        assertEquals("Longer than recorded human history", DecayTimeData.getComparison(5000.0))
        assertEquals("Effectively permanent - it will outlast humanity itself", DecayTimeData.getComparison(1500000.0))
    }

    @Test
    fun testCigaretteAndExtendedShapes_haveSpecificEntries() {
        val cig = DecayTimeData.getDecayInfo("cigarette", "Trash")
        assertEquals(10.0, cig.minYears, 0.001)
        assertEquals(12.0, cig.maxYears, 0.001)

        val bag = DecayTimeData.getDecayInfo("plastic_bag", "Trash")
        assertEquals(10.0, bag.minYears, 0.001)
        assertEquals(20.0, bag.maxYears, 0.001)

        val can = DecayTimeData.getDecayInfo("can", "Recyclable")
        assertEquals(80.0, can.minYears, 0.001)

        val bottle = DecayTimeData.getDecayInfo("bottle", "Recyclable")
        assertEquals(450.0, bottle.minYears, 0.001)

        val wine = DecayTimeData.getDecayInfo("wine glass", "Trash")
        assertTrue(wine.minYears >= 1000000.0)

        val banana = DecayTimeData.getDecayInfo("banana", "Organic")
        assertTrue(banana.minYears < 0.25)
    }

    @Test
    fun testInputsTrimmedAndNewFallbacks() {
        val trimmed = DecayTimeData.getDecayInfo("  Glass  ", "  Recyclable  ")
        assertEquals("Glass", trimmed.subclass)

        val hz = DecayTimeData.getDecayInfo("MysterySludge", "Hazardous")
        assertEquals(3, hz.severityLevel)

        val residual = DecayTimeData.getDecayInfo("MysteryMix", "Residual")
        assertEquals("Generic Trash", residual.subclass)

        val unknown = DecayTimeData.getDecayInfo("MysteryMix", "Unknown")
        assertEquals("Unknown Waste", unknown.subclass)
    }
}
