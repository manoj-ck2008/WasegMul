package com.agrelius.wasegmul

/**
 * Environmental impact metrics derived from waste segregation activity.
 */
data class EcoImpactMetrics(
    val totalWeightKg: Double,
    val co2PreventedKg: Double,
    val waterSavedLiters: Double,
    val treeYearEquivalent: Double,
    val totalItems: Int,
    val accuracyPercentage: Float?,
    val energySavedKwh: Double = 0.0
)

/**
 * Computes tangible environmental impact metrics based on EPA and international circular
 * economy recovery benchmarks.
 */
object EcoImpactCalculator {
    // Recycling 1kg mixed recyclables (paper, plastic, metal, glass) prevents ~1.5 kg CO2e
    private const val CO2_PER_KG_RECYCLABLE = 1.5
    // Composting 1kg organics prevents ~0.8 kg CO2e (avoids anaerobic methane generation in landfills)
    private const val CO2_PER_KG_ORGANIC = 0.8
    // Diverting 1kg e-waste prevents ~2.2 kg CO2e (mining & smelting virgin ores)
    private const val CO2_PER_KG_EWASTE = 2.2

    // Conserving water: ~15 liters saved per kg of recycled material (metals, paper, plastics)
    private const val WATER_LITERS_PER_KG_RECYCLABLE = 15.0

    // Energy conserved:
    // Recycling 1kg mixed recyclables saves ~4.2 kWh (metals, paper, plastics)
    private const val ENERGY_KWH_PER_KG_RECYCLABLE = 4.2
    // Composting avoids synthetic fertilizer production, saving ~0.3 kWh per kg
    private const val ENERGY_KWH_PER_KG_ORGANIC = 0.3
    // E-waste recycling avoids mineral mining/refining, saving ~6.5 kWh per kg
    private const val ENERGY_KWH_PER_KG_EWASTE = 6.5

    // Tree equivalent: One mature tree absorbs roughly 21.77 kg of CO2 per year
    private const val KG_CO2_PER_TREE_YEAR = 21.77

    fun calculate(records: List<WasteRecord>): EcoImpactMetrics {
        if (records.isEmpty()) {
            return EcoImpactMetrics(
                totalWeightKg = 0.0,
                co2PreventedKg = 0.0,
                waterSavedLiters = 0.0,
                treeYearEquivalent = 0.0,
                totalItems = 0,
                accuracyPercentage = null,
                energySavedKwh = 0.0
            )
        }

        var totalWeight = 0.0
        var co2 = 0.0
        var water = 0.0
        var energy = 0.0

        for (record in records) {
            val w = record.estimatedWeight
            if (!w.isFinite() || w <= 0.0) continue

            // If user marked incorrect with no correction, do not count toward diverted impact
            val isIncorrectWithoutCorrection = record.feedback?.trim().equals("incorrect", ignoreCase = true) &&
                record.correctedSubclass.isNullOrBlank()
            if (isIncorrectWithoutCorrection) continue

            // Use correctedSubclass if available to derive canonical category
            val cat = if (!record.correctedSubclass.isNullOrBlank()) {
                WasteMapping.getCategory(record.correctedSubclass)
            } else {
                record.category.trim()
            }

            // Uncertain/Unknown predictions carry no reliable weight: exclude from totals
            // to avoid inflating impact with guesses.
            if (cat.equals(WasteMapping.UNCERTAIN, ignoreCase = true) ||
                cat.equals(WasteMapping.UNKNOWN, ignoreCase = true)
            ) continue

            totalWeight += w
            when {
                cat.equals("Recyclable", ignoreCase = true) -> {
                    co2 += w * CO2_PER_KG_RECYCLABLE
                    water += w * WATER_LITERS_PER_KG_RECYCLABLE
                    energy += w * ENERGY_KWH_PER_KG_RECYCLABLE
                }
                cat.equals("Organic", ignoreCase = true) -> {
                    co2 += w * CO2_PER_KG_ORGANIC
                    energy += w * ENERGY_KWH_PER_KG_ORGANIC
                }
                cat.equals("E-Waste", ignoreCase = true) -> {
                    co2 += w * CO2_PER_KG_EWASTE
                    energy += w * ENERGY_KWH_PER_KG_EWASTE
                }
            }
        }

        val treeYears = if (co2 > 0.0) co2 / KG_CO2_PER_TREE_YEAR else 0.0

        val feedbackRecords = records.filter { it.feedback != null }
        val accuracy = if (feedbackRecords.isNotEmpty()) {
            val correct = feedbackRecords.count { it.feedback!!.trim().equals("correct", ignoreCase = true) }
            (correct.toFloat() / feedbackRecords.size) * 100f
        } else {
            null
        }

        return EcoImpactMetrics(
            totalWeightKg = totalWeight,
            co2PreventedKg = co2,
            waterSavedLiters = water,
            treeYearEquivalent = treeYears,
            totalItems = records.size,
            accuracyPercentage = accuracy,
            energySavedKwh = energy
        )
    }
}
