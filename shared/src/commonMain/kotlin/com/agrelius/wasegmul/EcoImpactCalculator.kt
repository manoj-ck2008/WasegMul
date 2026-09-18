package com.agrelius.wasegmul

import kotlinx.serialization.Serializable

/**
 * Environmental impact metrics derived from waste segregation activity.
 *
 * ## Field semantics (read carefully — two of these count different things)
 * - [totalItems]: EVERY record passed in, including excluded ones (zero-weight,
 *   user-flagged-incorrect without correction, Uncertain/Unknown). This is the
 *   "history size" number shown in the dashboard header.
 * - [divertedItems]: only records that actually earned diversion credit (positive weight,
 *   not excluded, known category). The honest "diverted" count.
 * - [totalWeightKg]: weight of credited records PLUS plain-[Trash][WasteMapping.TRASH]
 *   landfill weight (Trash is weighed but earns no CO₂/water/energy credit). I.e. this is
 *   "all weighed waste handled", NOT "waste diverted" — the name is kept for history compat.
 * - [divertedWeightKg]: weight that earned diversion credit (excludes Trash/unknown).
 * Use [divertedItems]/[divertedWeightKg] for any "you diverted X" claim.
 */
@Serializable
data class EcoImpactMetrics(
    val totalWeightKg: Double,
    val co2PreventedKg: Double,
    val waterSavedLiters: Double,
    val treeYearEquivalent: Double,
    val totalItems: Int,
    val accuracyPercentage: Float?,
    val energySavedKwh: Double = 0.0,
    /** Records that earned diversion credit (see class KDoc). */
    val divertedItems: Int = 0,
    /** Credited weight in kg (excludes Trash/unknown; see class KDoc). */
    val divertedWeightKg: Double = 0.0,
    /**
     * Records whose `correctedSubclass` was a typo/unmapped label and therefore fell back
     * to the ORIGINAL category (see [EcoImpactCalculator.calculate]). Surfaced — never
     * silently dropped.
     */
    val fallbackCorrectionItems: Int = 0
)

/**
 * Computes tangible environmental impact metrics from waste segregation activity.
 *
 * ## Factor provenance (READ BEFORE quoting these as precise)
 * Per-category factors below are ORDER-OF-MAGNITUDE planning estimates aligned with
 * `docs/architecture.md § Mathematical Multipliers` (single source of truth — values
 * verified equal), NOT EPA-precise per-material science:
 * - They are single per-category averages. Real savings vary enormously within a category
 *   (e.g. `Recyclable` energy 4.2 kWh/kg over-credits glass ~0.1 and under-credits
 *   aluminium ~13; E-waste CO₂ 3.5 kg/kg is low vs a phone LCA of 10+).
 * - `Hazardous` factors are internal estimates for safe-channel diversion (collection +
 *   specialist handling), NOT an EPA table — do not cite them as such.
 * Present results to users as estimates ("≈") and cite ranges, never false precision.
 * Per-material refinement is tracked future work.
 */
object EcoImpactCalculator {
    // Aligned with docs/architecture.md § Mathematical Multipliers (single source of truth).
    // Units: kg CO2e avoided per kg waste; litres water per kg; kWh per kg.
    // Ranges (documented, not precise): Recyclable CO2 0.5–3.0 depending on material;
    // E-waste CO2 3.5 here vs 10+ in phone LCAs (conservative); Hazardous = internal estimate.
    private const val CO2_PER_KG_RECYCLABLE = 1.8
    private const val CO2_PER_KG_ORGANIC = 0.5
    private const val CO2_PER_KG_EWASTE = 3.5
    private const val CO2_PER_KG_HAZARDOUS = 2.2

    private const val WATER_LITERS_PER_KG_RECYCLABLE = 25.0
    private const val WATER_LITERS_PER_KG_ORGANIC = 2.0
    private const val WATER_LITERS_PER_KG_EWASTE = 15.0
    private const val WATER_LITERS_PER_KG_HAZARDOUS = 10.0

    // Energy conserved:
    private const val ENERGY_KWH_PER_KG_RECYCLABLE = 4.2
    // Composting avoids synthetic fertilizer production, saving ~0.3 kWh per kg
    private const val ENERGY_KWH_PER_KG_ORGANIC = 0.3
    // E-waste recycling avoids mineral mining/refining, saving ~6.5 kWh per kg
    private const val ENERGY_KWH_PER_KG_EWASTE = 6.5
    private const val ENERGY_KWH_PER_KG_HAZARDOUS = 3.0

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
        var divertedWeight = 0.0
        var divertedCount = 0
        var fallbackCorrections = 0
        var co2 = 0.0
        var water = 0.0
        var energy = 0.0

        for (record in records) {
            val w = record.estimatedWeight
            if (!w.isFinite() || w <= 0.0) continue

            // If user marked incorrect with no correction, do not count toward diverted impact
            val isIncorrectWithoutCorrection =
                record.feedback?.trim().equals(WasteFeedback.INCORRECT, ignoreCase = true) &&
                    record.correctedSubclass.isNullOrBlank()
            if (isIncorrectWithoutCorrection) continue

            // Use correctedSubclass if available to derive canonical category.
            // Typo'd/unmapped corrections fall back to the ORIGINAL category and are
            // counted in fallbackCorrectionItems — never silently dropped.
            val rawCorrection = record.correctedSubclass?.trim().orEmpty()
            val (cat, usedFallback) = if (rawCorrection.isNotBlank()) {
                val mapped = WasteMapping.getCategory(rawCorrection)
                if (mapped == WasteMapping.UNKNOWN) {
                    record.category.trim() to true
                } else {
                    mapped to false
                }
            } else {
                record.category.trim() to false
            }
            if (usedFallback) fallbackCorrections++

            // Uncertain/Unknown predictions carry no reliable weight: exclude from totals
            // to avoid inflating impact with guesses.
            if (WasteMapping.isSentinel(cat) || cat.equals(WasteMapping.UNKNOWN, ignoreCase = true)) continue

            totalWeight += w
            // Hazardous flag takes precedence in EVERY category: a hazardous item earns
            // diversion credit at the BETTER of its category rate and the hazardous rate,
            // so e.g. a hazardous Recyclable is never credited below safe-channel value
            // while high-value streams (E-waste 3.5 > hazardous 2.2) keep their own rate.
            val effectiveSubclass = rawCorrection.ifBlank { record.subclass }
            val isHazardousItem = cat.equals("Hazardous", ignoreCase = true) ||
                WasteMapping.isHazardous(effectiveSubclass)
            fun credit(catCo2: Double, catWater: Double, catEnergy: Double) {
                co2 += w * maxOf(catCo2, if (isHazardousItem) CO2_PER_KG_HAZARDOUS else Double.NEGATIVE_INFINITY)
                water += w * maxOf(catWater, if (isHazardousItem) WATER_LITERS_PER_KG_HAZARDOUS else Double.NEGATIVE_INFINITY)
                energy += w * maxOf(catEnergy, if (isHazardousItem) ENERGY_KWH_PER_KG_HAZARDOUS else Double.NEGATIVE_INFINITY)
            }
            val credited = when {
                cat.equals("Recyclable", ignoreCase = true) -> {
                    credit(CO2_PER_KG_RECYCLABLE, WATER_LITERS_PER_KG_RECYCLABLE, ENERGY_KWH_PER_KG_RECYCLABLE); true
                }
                cat.equals("Organic", ignoreCase = true) -> {
                    credit(CO2_PER_KG_ORGANIC, WATER_LITERS_PER_KG_ORGANIC, ENERGY_KWH_PER_KG_ORGANIC); true
                }
                cat.equals("E-Waste", ignoreCase = true) -> {
                    credit(CO2_PER_KG_EWASTE, WATER_LITERS_PER_KG_EWASTE, ENERGY_KWH_PER_KG_EWASTE); true
                }
                cat.equals("Hazardous", ignoreCase = true) ||
                    ((cat.equals(WasteMapping.TRASH, ignoreCase = true) ||
                        cat.equals(WasteMapping.RESIDUAL, ignoreCase = true)) && isHazardousItem) -> {
                    credit(CO2_PER_KG_HAZARDOUS, WATER_LITERS_PER_KG_HAZARDOUS, ENERGY_KWH_PER_KG_HAZARDOUS); true
                }
                // Plain Trash / Residual (CONTEXT.md name for Trash, normalized by
                // normalizeCategoryLabel): counted in weight only, no diversion credit.
                else -> false
            }
            if (credited) {
                divertedWeight += w
                divertedCount++
            }
        }

        val treeYears = if (co2 > 0.0) co2 / KG_CO2_PER_TREE_YEAR else 0.0

        // Accuracy: explicit "correct" marks over ALL rated records. Any non-"correct"
        // rated value counts as not-correct (conservative); unrated records are excluded.
        // Comparison is trimmed + case-insensitive (locale-independent ASCII vocab).
        val feedbackRecords = records.filter { WasteFeedback.isKnown(it.feedback) }
        val accuracy = if (feedbackRecords.isNotEmpty()) {
            val correct = feedbackRecords.count { WasteFeedback.isCorrectMark(it.feedback) }
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
            energySavedKwh = energy,
            divertedItems = divertedCount,
            divertedWeightKg = divertedWeight,
            fallbackCorrectionItems = fallbackCorrections
        )
    }

    /**
     * Flat/parallel-array overload for Swift (Obj-C-hostile `List<WasteRecord>` alternative).
     * Arrays must be parallel: index `i` across all arrays describes one record. Shorter
     * arrays truncate to the shortest (documented lenient contract — use [calculateStrict]
     * for a throwing variant). Weights are in **kg**; confidences in `0..1`.
     */
    fun calculateFlat(
        categories: List<String>,
        subclasses: List<String>,
        weightsKg: List<Double>,
        confidences: List<Float>,
        feedbacks: List<String?> = emptyList(),
        correctedSubclasses: List<String?> = emptyList()
    ): EcoImpactMetrics {
        val n = minOf(
            categories.size, subclasses.size, weightsKg.size, confidences.size,
            feedbacks.size.takeIf { it > 0 } ?: Int.MAX_VALUE,
            correctedSubclasses.size.takeIf { it > 0 } ?: Int.MAX_VALUE
        )
        if (n <= 0) return calculate(emptyList())
        val records = (0 until n).map { i ->
            WasteRecord(
                category = categories[i],
                subclass = subclasses[i],
                confidence = confidences[i].let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f },
                estimatedWeight = weightsKg[i].let { if (it.isFinite() && it >= 0.0) it else 0.0 },
                feedback = feedbacks.getOrNull(i),
                correctedSubclass = correctedSubclasses.getOrNull(i)
            )
        }
        return calculate(records)
    }

    /**
     * Strict variant of [calculateFlat]: requires all arrays to have equal size and throws
     * [IllegalArgumentException] otherwise (no silent truncation).
     */
    fun calculateFlatStrict(
        categories: List<String>,
        subclasses: List<String>,
        weightsKg: List<Double>,
        confidences: List<Float>,
        feedbacks: List<String?> = emptyList(),
        correctedSubclasses: List<String?> = emptyList()
    ): EcoImpactMetrics {
        val sizes = listOf(categories.size, subclasses.size, weightsKg.size, confidences.size) +
            (feedbacks.size.takeIf { it > 0 }?.let(::listOf) ?: emptyList()) +
            (correctedSubclasses.size.takeIf { it > 0 }?.let(::listOf) ?: emptyList())
        require(sizes.distinct().size == 1) { "parallel arrays must have equal size, was $sizes" }
        return calculateFlat(categories, subclasses, weightsKg, confidences, feedbacks, correctedSubclasses)
    }
}
