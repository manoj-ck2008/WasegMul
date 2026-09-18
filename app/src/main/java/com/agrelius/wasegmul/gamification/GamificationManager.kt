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

    fun calculateXpForScan(
        category: String,
        confidence: Float,
        co2PreventedGrams: Double,
        dailyScanCount: Int
    ): Int {
        val baseXp = 10
        val categoryMultiplier = when (category) {
            "E-Waste" -> 3.0
            "Recyclable" -> 2.0
            "Organic" -> 1.5
            "Trash" -> 1.0
            else -> 1.0
        }
        val co2Bonus = floor(co2PreventedGrams / 10.0).toInt()
        val confidenceBonus = if (confidence >= 0.85f) 5 else 0
        val streakBonus = if (dailyScanCount >= 3) 10 else 0
        
        val total = floor(baseXp * categoryMultiplier).toInt() + co2Bonus + confidenceBonus + streakBonus
        return total.coerceAtLeast(5)
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
        dailyScanCount: Int
    ): XpGainResult {
        val xpEarned = calculateXpForScan(category, confidence, co2PreventedGrams, dailyScanCount)
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
