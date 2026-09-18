package com.agrelius.wasegmul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcoImpactTest {

    @Test
    fun calculate_emptyHistory_returnsZeroMetrics() {
        val metrics = EcoImpactCalculator.calculate(emptyList())
        assertEquals(0.0, metrics.totalWeightKg, 0.001)
        assertEquals(0.0, metrics.co2PreventedKg, 0.001)
        assertEquals(0.0, metrics.waterSavedLiters, 0.001)
        assertEquals(0.0, metrics.treeYearEquivalent, 0.001)
        assertEquals(0, metrics.totalItems)
        assertEquals(null, metrics.accuracyPercentage)
    }

    @Test
    fun calculate_mixedWasteRecords_computesCorrectEnvironmentalOffsets() {
        val records = listOf(
            WasteRecord(id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.9f, estimatedWeight = 1.0),
            WasteRecord(id = 2, category = "Organic", subclass = "Organic", confidence = 0.85f, estimatedWeight = 2.0),
            WasteRecord(id = 3, category = "E-Waste", subclass = "Battery", confidence = 0.95f, estimatedWeight = 0.5),
            WasteRecord(id = 4, category = "Trash", subclass = "Miscellaneous Trash", confidence = 0.7f, estimatedWeight = 0.1)
        )

        val metrics = EcoImpactCalculator.calculate(records)

        // Total weight = 1.0 + 2.0 + 0.5 + 0.1 = 3.6 kg
        assertEquals(3.6, metrics.totalWeightKg, 0.001)

        // CO2 prevented (docs/architecture.md multipliers):
        // Recyclable: 1.0 * 1.8 = 1.8 kg
        // Organic: 2.0 * 0.5 = 1.0 kg
        // E-Waste: 0.5 * 3.5 = 1.75 kg
        // Trash: 0 kg
        // Total CO2: 1.8 + 1.0 + 1.75 = 4.55 kg
        assertEquals(4.55, metrics.co2PreventedKg, 0.001)

        // Water saved: Recyclable 1.0*25 + Organic 2.0*2.0 + E-Waste 0.5*15.0 = 36.5 L
        assertEquals(36.5, metrics.waterSavedLiters, 0.001)

        // Tree-year equivalent: 4.55 / 21.77
        val expectedTreeYears = 4.55 / 21.77
        assertEquals(expectedTreeYears, metrics.treeYearEquivalent, 0.001)
        assertEquals(4, metrics.totalItems)

        // Energy saved:
        // Recyclable: 1.0 * 4.2 = 4.2 kWh
        // Organic: 2.0 * 0.3 = 0.6 kWh
        // E-Waste: 0.5 * 6.5 = 3.25 kWh
        // Total Energy: 4.2 + 0.6 + 3.25 = 8.05 kWh
        assertEquals(8.05, metrics.energySavedKwh, 0.001)
    }

    @Test
    fun calculate_withUserFeedback_computesAccuracyPercentage() {
        val records = listOf(
            WasteRecord(id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.9f, feedback = "correct"),
            WasteRecord(id = 2, category = "Recyclable", subclass = "Metal", confidence = 0.8f, feedback = "correct"),
            WasteRecord(id = 3, category = "Organic", subclass = "Organic", confidence = 0.7f, feedback = "incorrect"),
            WasteRecord(id = 4, category = "Trash", subclass = "Miscellaneous Trash", confidence = 0.6f, feedback = null)
        )

        val metrics = EcoImpactCalculator.calculate(records)
        // 3 with feedback, 2 correct -> 2/3 = 66.67%
        assertNotNull(metrics.accuracyPercentage)
        assertEquals(66.666f, metrics.accuracyPercentage!!, 0.1f)
    }

    @Test
    fun calculate_withCorrection_usesCorrectedCategory() {
        val records = listOf(
            // Initially marked Trash (0 kg CO2 offset), but user corrected to Battery (E-Waste)
            WasteRecord(
                id = 1,
                category = "Trash",
                subclass = "Miscellaneous Trash",
                confidence = 0.7f,
                estimatedWeight = 1.0,
                feedback = "incorrect",
                correctedSubclass = "Battery"
            )
        )
        val metrics = EcoImpactCalculator.calculate(records)
        // Battery maps to E-Waste -> 1.0 kg * 3.5 = 3.5 kg CO2, 6.5 kWh energy, 15.0 L water
        assertEquals(3.5, metrics.co2PreventedKg, 0.001)
        assertEquals(6.5, metrics.energySavedKwh, 0.001)
    }

    @Test
    fun calculate_incorrectFeedbackWithoutCorrection_excludedFromDivertedTotals() {
        val records = listOf(
            // User flagged as incorrect, but provided no correction -> excluded from positive offsets
            WasteRecord(
                id = 1,
                category = "Recyclable",
                subclass = "Plastic",
                confidence = 0.7f,
                estimatedWeight = 1.0,
                feedback = "incorrect",
                correctedSubclass = null
            )
        )
        val metrics = EcoImpactCalculator.calculate(records)
        assertEquals(0.0, metrics.totalWeightKg, 0.001)
        assertEquals(0.0, metrics.co2PreventedKg, 0.001)
        assertEquals(0.0, metrics.energySavedKwh, 0.001)
        assertEquals(1, metrics.totalItems)
    }

    @Test
    fun calculate_hazardousTrash_earnsDiversionCredit() {
        val records = listOf(
            WasteRecord(id = 1, category = "Trash", subclass = "light bulbs", confidence = 0.9f, estimatedWeight = 1.0)
        )
        val metrics = EcoImpactCalculator.calculate(records)
        // Hazardous: 1.0 * 2.2 CO2, 10.0 water, 3.0 energy
        assertEquals(2.2, metrics.co2PreventedKg, 0.001)
        assertEquals(10.0, metrics.waterSavedLiters, 0.001)
        assertEquals(3.0, metrics.energySavedKwh, 0.001)
    }

    @Test
    fun calculate_divertedCountsOnlyCredited() {
        val records = listOf(
            WasteRecord(id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.9f, estimatedWeight = 1.0),
            WasteRecord(id = 2, category = "Trash", subclass = "Miscellaneous Trash", confidence = 0.7f, estimatedWeight = 0.5),
            WasteRecord(
                id = 3, category = "Recyclable", subclass = "Plastic", confidence = 0.7f,
                estimatedWeight = 2.0, feedback = "incorrect", correctedSubclass = null
            )
        )
        val metrics = EcoImpactCalculator.calculate(records)
        assertEquals(3, metrics.totalItems)
        assertEquals(1.5, metrics.totalWeightKg, 0.001)
        assertEquals(1, metrics.divertedItems)
        assertEquals(1.0, metrics.divertedWeightKg, 0.001)
    }

    @Test
    fun calculate_typoCorrection_fallsBackAndFlags() {
        val records = listOf(
            WasteRecord(
                id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.8f,
                estimatedWeight = 1.0, feedback = "incorrect", correctedSubclass = "Plastc-typo-99"
            )
        )
        val metrics = EcoImpactCalculator.calculate(records)
        assertEquals(1.8, metrics.co2PreventedKg, 0.001)
        assertEquals(1, metrics.fallbackCorrectionItems)
    }

    // NOTE (placement): this is a Knowledge-Base/Mapping integrity probe, not an
    // EcoImpact assertion — its canonical home is WasteKnowledgeBaseTest (which owns
    // the full guidance suite). Kept here as a cheap cross-module smoke signal only;
    // do not grow KB coverage in this file.
    @Test
    fun wasteKnowledgeBase_all30ClassesHaveDetailedGuidance() {
        assertEquals(30, WasteMapping.MAPPING.size)

        for ((subclass, meta) in WasteMapping.MAPPING) {
            val info = WasteKnowledgeBase.getInfo(meta.category, subclass)
            assertTrue("Guide for $subclass should not be blank", info.disposalGuide.isNotBlank())
            assertTrue("Environmental impact for $subclass should not be blank", info.environmentalImpact.isNotBlank())
            assertTrue("Recycling benefits for $subclass should not be blank", info.recyclingBenefits.isNotBlank())
            assertTrue("Sources for $subclass should not be blank", info.sources.isNotBlank())
            assertTrue("Weight for $subclass must be positive", meta.weightKg > 0.0)
        }
    }
}
