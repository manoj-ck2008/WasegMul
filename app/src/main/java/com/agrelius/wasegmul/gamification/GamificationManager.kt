package com.agrelius.wasegmul.gamification

import kotlin.math.floor

data class EcoLevel(
    val level: Int,
    val xpRequired: Int,
    val name: String,
    val iconEmoji: String
)

data class GamificationState(
    val currentXp: Int,
    val currentLevel: EcoLevel,
    val nextLevel: EcoLevel?,
    val progressToNextLevel: Float,
    val xpToNextLevel: Int
)

data class XpGainResult(
    val xpEarned: Int,
    val newTotalXp: Int,
    val previousLevel: EcoLevel,
    val newLevel: EcoLevel,
    val didLevelUp: Boolean,
    val co2PreventedGrams: Double
)

object GamificationManager {
    const val MAX_CO2_BONUS_XP = 100
    const val MAX_TOTAL_XP_PER_SCAN = 150

    /**
     * Scans below this confidence earn NO XP (§3.44): `coerceAtLeast(5)` previously
     * rewarded Unknown / near-zero-confidence / duplicate scans, enabling spam-leveling
     * (repeatedly scanning the desk earns a level). Unknown/UNCERTAIN categories are
     * likewise worth 0 — an abstain is not an achievement.
     */
    const val MIN_CONFIDENCE_FOR_XP = 0.35f

    /**
     * Repeat window for the same subclass: a second award for an identical subclass
     * inside this window is treated as a duplicate tap, not a new scan (§3.44).
     * Pure helper [isDuplicateScan]; callers pass the last award via [processNewScan].
     */
    const val DEDUP_WINDOW_MS = 60_000L
    val LEVELS = listOf(
        EcoLevel(1, 0, "Eco Seed", "🌱"),
        EcoLevel(2, 100, "Green Sprout", "🌿"),
        EcoLevel(3, 300, "Eco Explorer", "🍃"),
        EcoLevel(4, 750, "Waste Warrior", "♻️"),
        EcoLevel(5, 1500, "Green Guardian", "🛡️"),
        EcoLevel(6, 3000, "Earth Protector", "🌍"),
        EcoLevel(7, 6000, "Eco Champion", "🏆"),
        EcoLevel(8, 10000, "Planet Hero", "💎"),
        EcoLevel(9, 20000, "Sustainability Master", "🌳"),
        EcoLevel(10, 50000, "Planet Guardian", "🌏")
    )

    /**
     * Category → XP multiplier, aligned to CONTEXT.md tiers. `Residual` is the
     * canonical name for curbside general waste; legacy `Trash` scores identically
     * (compat — runtime history still stores it). `Hazardous` outranks Recyclable:
     * keeping toxics out of landfill is the highest-leverage citizen action.
     */
    fun multiplierForCategory(category: String): Double = when (category.trim()) {
        "E-Waste" -> 3.0
        "Hazardous" -> 2.5
        "Recyclable" -> 2.0
        "Organic" -> 1.5
        "Residual", "Trash" -> 1.0
        else -> 1.0
    }

    /** True when [category] is a sentinel abstain (never earns XP). */
    fun isSentinelCategory(category: String): Boolean {
        val trimmed = category.trim()
        return trimmed.equals("Unknown", ignoreCase = true) ||
            trimmed.equals("Uncertain", ignoreCase = true) ||
            trimmed.isEmpty()
    }

    /**
     * Pure duplicate-tap check: same subclass (case-insensitive, trimmed) re-scanned
     * within [windowMs] of the last awarded scan. Null last-scan state → not a dupe.
     */
    fun isDuplicateScan(
        subclass: String,
        timestampMs: Long,
        lastSubclass: String?,
        lastTimestampMs: Long?,
        windowMs: Long = DEDUP_WINDOW_MS
    ): Boolean {
        if (lastSubclass == null || lastTimestampMs == null) return false
        if (!subclass.trim().equals(lastSubclass.trim(), ignoreCase = true)) return false
        val elapsed = timestampMs - lastTimestampMs
        return elapsed in 0 until windowMs
    }

    fun calculateXpForScan(
        category: String,
        confidence: Float,
        co2PreventedGrams: Double,
        dailyScanCount: Int
    ): Int {
        if (!confidence.isFinite() || confidence < MIN_CONFIDENCE_FOR_XP) return 0
        if (isSentinelCategory(category)) return 0
        val baseXp = 10
        val categoryMultiplier = multiplierForCategory(category)
        // Clamp negative CO₂ (bad sensor/estimate input) to 0 BEFORE the bonus math:
        // the old code floored negatives and then clamped the total, so a -500 g
        // glitch silently ate the base award instead of being ignored.
        val safeCo2Grams = co2PreventedGrams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        // Cap per-scan CO2 bonus so a single 75kg appliance (165,000g CO2) cannot
        // jump L1 -> L8 in one scan. 100 XP cap preserves progression economy.
        val co2Bonus = floor(safeCo2Grams / 10.0).toInt().coerceAtMost(MAX_CO2_BONUS_XP)
        val confidenceBonus = if (confidence >= 0.85f) 5 else 0
        val streakBonus = if (dailyScanCount >= 3) 10 else 0

        val total = floor(baseXp * categoryMultiplier).toInt() + co2Bonus + confidenceBonus + streakBonus
        return total.coerceAtLeast(5).coerceAtMost(MAX_TOTAL_XP_PER_SCAN)
    }

    fun getLevelForXp(totalXp: Int): EcoLevel {
        return LEVELS.lastOrNull { it.xpRequired <= totalXp } ?: LEVELS.first()
    }

    fun computeState(totalXp: Int): GamificationState {
        val currentLevel = getLevelForXp(totalXp)
        val currentIndex = LEVELS.indexOf(currentLevel)
        val nextLevel = if (currentIndex + 1 < LEVELS.size) LEVELS[currentIndex + 1] else null
        
        val progress = if (nextLevel != null) {
            val xpIntoLevel = totalXp - currentLevel.xpRequired
            val levelXpSpan = nextLevel.xpRequired - currentLevel.xpRequired
            (xpIntoLevel.toFloat() / levelXpSpan.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        
        val xpToNextLevel = nextLevel?.xpRequired?.minus(totalXp)?.coerceAtLeast(0) ?: 0
        
        return GamificationState(
            currentXp = totalXp,
            currentLevel = currentLevel,
            nextLevel = nextLevel,
            progressToNextLevel = progress,
            xpToNextLevel = xpToNextLevel
        )
    }

    fun processNewScan(
        currentTotalXp: Int,
        category: String,
        confidence: Float,
        co2PreventedGrams: Double,
        dailyScanCount: Int,
        subclass: String = "",
        scanTimestampMs: Long = System.currentTimeMillis(),
        lastSubclass: String? = null,
        lastScanTimestampMs: Long? = null
    ): XpGainResult {
        val xpEarned = if (
            subclass.isNotBlank() &&
            isDuplicateScan(subclass, scanTimestampMs, lastSubclass, lastScanTimestampMs)
        ) {
            0
        } else {
            calculateXpForScan(category, confidence, co2PreventedGrams, dailyScanCount)
        }
        val newTotalXp = currentTotalXp + xpEarned
        val previousLevel = getLevelForXp(currentTotalXp)
        val newLevel = getLevelForXp(newTotalXp)
        val didLevelUp = newLevel.level > previousLevel.level
        
        return XpGainResult(
            xpEarned = xpEarned,
            newTotalXp = newTotalXp,
            previousLevel = previousLevel,
            newLevel = newLevel,
            didLevelUp = didLevelUp,
            co2PreventedGrams = co2PreventedGrams
        )
    }
}
