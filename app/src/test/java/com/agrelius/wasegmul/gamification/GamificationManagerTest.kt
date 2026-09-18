package com.agrelius.wasegmul.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationManagerTest {

    @Test
    fun testLevelHierarchy() {
        assertEquals(10, GamificationManager.LEVELS.size)
        assertEquals(1, GamificationManager.LEVELS[0].level)
        assertEquals(0, GamificationManager.LEVELS[0].xpRequired)
        assertEquals("Eco Seed", GamificationManager.LEVELS[0].name)

        assertEquals(10, GamificationManager.LEVELS[9].level)
        assertEquals(50000, GamificationManager.LEVELS[9].xpRequired)
        assertEquals("Planet Guardian", GamificationManager.LEVELS[9].name)
    }

    @Test
    fun testCalculateXp() {
        // Base Recyclable scan with low confidence, no co2, no streak
        // 10 * 2.0 = 20
        val baseRecyclable = GamificationManager.calculateXpForScan(
            category = "Recyclable",
            confidence = 0.70f,
            co2PreventedGrams = 0.0,
            dailyScanCount = 1
        )
        assertEquals(20, baseRecyclable)

        // E-Waste scan (3x) + confidence bonus (5) + co2 bonus (50g -> 5) + streak (10)
        // 10 * 3.0 = 30 + 5 + 5 + 10 = 50
        val highImpactEWaste = GamificationManager.calculateXpForScan(
            category = "E-Waste",
            confidence = 0.95f,
            co2PreventedGrams = 50.0,
            dailyScanCount = 3
        )
        assertEquals(50, highImpactEWaste)
    }

    @Test
    fun testGetLevelForXp() {
        assertEquals(1, GamificationManager.getLevelForXp(0).level)
        assertEquals(1, GamificationManager.getLevelForXp(99).level)
        assertEquals(2, GamificationManager.getLevelForXp(100).level)
        assertEquals(2, GamificationManager.getLevelForXp(299).level)
        assertEquals(3, GamificationManager.getLevelForXp(300).level)
        assertEquals(10, GamificationManager.getLevelForXp(50000).level)
        assertEquals(10, GamificationManager.getLevelForXp(999999).level)
    }

    @Test
    fun testComputeState() {
        val state = GamificationManager.computeState(150)
        assertEquals(2, state.currentLevel.level)
        assertEquals("Green Sprout", state.currentLevel.name)
        assertEquals(3, state.nextLevel?.level)
        assertEquals("Eco Explorer", state.nextLevel?.name)
        // Level 2 is 100 to 300 (span = 200). 150 - 100 = 50. 50/200 = 0.25f
        assertEquals(0.25f, state.progressToNextLevel, 0.001f)
        assertEquals(150, state.xpToNextLevel)

        // Max level state
        val maxState = GamificationManager.computeState(60000)
        assertEquals(10, maxState.currentLevel.level)
        assertNull(maxState.nextLevel)
        assertEquals(1.0f, maxState.progressToNextLevel, 0.001f)
        assertEquals(0, maxState.xpToNextLevel)
    }

    @Test
    fun testProcessNewScanLevelUp() {
        // Start at 90 XP (Level 1). Earn 30 XP -> 120 XP (Level 2)
        val result = GamificationManager.processNewScan(
            currentTotalXp = 90,
            category = "Recyclable",
            confidence = 0.90f, // +5
            co2PreventedGrams = 50.0, // +5
            dailyScanCount = 1
        )
        // 20 + 5 + 5 = 30 XP
        assertEquals(30, result.xpEarned)
        assertEquals(120, result.newTotalXp)
        assertEquals(1, result.previousLevel.level)
        assertEquals(2, result.newLevel.level)
        assertTrue(result.didLevelUp)

        // Same level scan: from 120 XP earn 20 XP -> 140 XP (still Level 2)
        val sameLevelResult = GamificationManager.processNewScan(
            currentTotalXp = 120,
            category = "Recyclable",
            confidence = 0.70f,
            co2PreventedGrams = 0.0,
            dailyScanCount = 1
        )
        assertEquals(20, sameLevelResult.xpEarned)
        assertEquals(140, sameLevelResult.newTotalXp)
        assertFalse(sameLevelResult.didLevelUp)
    }

    @Test
    fun testCo2BonusCapped_preventsInstantMaxLevel() {
        // 75kg refrigerator ≈ 165,000g CO2 would previously award 16,500 XP (L1->L8).
        val heavyScan = GamificationManager.calculateXpForScan(
            category = "E-Waste",
            confidence = 0.95f,
            co2PreventedGrams = 165000.0,
            dailyScanCount = 1
        )
        assertTrue("Heavy scan XP must be capped, was $heavyScan", heavyScan <= GamificationManager.MAX_TOTAL_XP_PER_SCAN)
        assertEquals(GamificationManager.MAX_CO2_BONUS_XP, 100)
    }

    @Test
    fun lowConfidenceAndSentinelCategory_earnZeroXp() {
        // Spam-leveling guard: Unknown / low-confidence scans are abstains, not achievements.
        assertEquals(0, GamificationManager.calculateXpForScan("Recyclable", 0.10f, 50.0, 5))
        assertEquals(0, GamificationManager.calculateXpForScan("Recyclable", Float.NaN, 50.0, 5))
        assertEquals(0, GamificationManager.calculateXpForScan("Unknown", 0.95f, 50.0, 5))
        assertEquals(0, GamificationManager.calculateXpForScan("Uncertain", 0.95f, 50.0, 5))
        assertEquals(0, GamificationManager.calculateXpForScan("", 0.95f, 50.0, 5))
    }

    @Test
    fun duplicateScan_withinWindow_earnsZero() {
        val dupe = GamificationManager.processNewScan(
            currentTotalXp = 100,
            category = "Recyclable",
            confidence = 0.9f,
            co2PreventedGrams = 20.0,
            dailyScanCount = 1,
            subclass = "Plastic Bottle",
            scanTimestampMs = 10_000L,
            lastSubclass = "plastic bottle",
            lastScanTimestampMs = 5_000L
        )
        assertEquals(0, dupe.xpEarned)
        assertEquals(100, dupe.newTotalXp)
        assertFalse(dupe.didLevelUp)

        // Outside the window the same subclass earns normally again.
        val fresh = GamificationManager.processNewScan(
            currentTotalXp = 100,
            category = "Recyclable",
            confidence = 0.9f,
            co2PreventedGrams = 20.0,
            dailyScanCount = 1,
            subclass = "Plastic Bottle",
            scanTimestampMs = 200_000L,
            lastSubclass = "Plastic Bottle",
            lastScanTimestampMs = 5_000L
        )
        assertTrue(fresh.xpEarned > 0)

        // Different subclass inside the window still earns.
        val other = GamificationManager.processNewScan(
            currentTotalXp = 100,
            category = "Recyclable",
            confidence = 0.9f,
            co2PreventedGrams = 20.0,
            dailyScanCount = 1,
            subclass = "Glass Bottle",
            scanTimestampMs = 10_000L,
            lastSubclass = "Plastic Bottle",
            lastScanTimestampMs = 5_000L
        )
        assertTrue(other.xpEarned > 0)
    }

    @Test
    fun multipliers_residualTrashHazardous() {
        // Residual == Trash == 1.0x: 10 base, no bonuses at 0.70 conf / 0 CO2 / count 1.
        assertEquals(10, GamificationManager.calculateXpForScan("Residual", 0.70f, 0.0, 1))
        assertEquals(10, GamificationManager.calculateXpForScan("Trash", 0.70f, 0.0, 1))
        // Hazardous 2.5x (CONTEXT tier): keeping toxics out of landfill outranks recycling.
        assertEquals(25, GamificationManager.calculateXpForScan("Hazardous", 0.70f, 0.0, 1))
    }

    @Test
    fun negativeCo2_clampedToZero_noPenalty() {
        // A -500 g glitch must not eat the base award (was: floor(-50) + 20 → clamped oddly).
        assertEquals(20, GamificationManager.calculateXpForScan("Recyclable", 0.70f, -500.0, 1))
    }
}
