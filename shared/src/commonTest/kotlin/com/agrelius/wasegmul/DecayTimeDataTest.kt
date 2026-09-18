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
        assertEquals("Less than a single season", DecayTimeData.getComparison(0.5))
        assertEquals("Longer than a generation", DecayTimeData.getComparison(25.0))
        assertEquals("Your grandchildren would still see it", DecayTimeData.getComparison(75.0))
        assertEquals("Outlasts every building standing today", DecayTimeData.getComparison(200.0))
        assertEquals("Longer than most civilizations have existed", DecayTimeData.getComparison(800.0))
        assertEquals("Longer than recorded human history", DecayTimeData.getComparison(5000.0))
        assertEquals("Effectively permanent - it will outlast humanity itself", DecayTimeData.getComparison(1500000.0))
    }
}
