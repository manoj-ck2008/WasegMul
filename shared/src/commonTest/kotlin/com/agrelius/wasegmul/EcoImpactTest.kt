package com.agrelius.wasegmul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seam tests for audit §3.51 fixes. Factor expectations mirror docs/architecture.md
 * (independent truth); new-field expectations mirror the documented field semantics.
 */
class EcoImpactTest {

    @Test
    fun emptyHistory_returnsZeroMetrics() {
        val m = EcoImpactCalculator.calculate(emptyList())
        assertEquals(0.0, m.totalWeightKg, 0.001)
        assertEquals(0, m.totalItems)
        assertEquals(0, m.divertedItems)
        assertEquals(0.0, m.divertedWeightKg, 0.001)
        assertNull(m.accuracyPercentage)
    }

    @Test
    fun totalItemsCountsAll_divertedCountsCreditedOnly() {
        val records = listOf(
            WasteRecord(id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.9f, estimatedWeight = 1.0),
            WasteRecord(id = 2, category = "Trash", subclass = "Miscellaneous Trash", confidence = 0.7f, estimatedWeight = 0.5),
            WasteRecord(
                id = 3, category = "Recyclable", subclass = "Plastic", confidence = 0.7f,
                estimatedWeight = 2.0, feedback = "incorrect", correctedSubclass = null
            ),
            WasteRecord(id = 4, category = "Uncertain", subclass = "Uncertain", confidence = 0f, estimatedWeight = 1.0)
        )
        val m = EcoImpactCalculator.calculate(records)
        // totalItems = all 4 (history size); totalWeight = credited 1.0 + Trash 0.5.
        assertEquals(4, m.totalItems)
        assertEquals(1.5, m.totalWeightKg, 0.001)
        // Diverted = only the credited Recyclable record.
        assertEquals(1, m.divertedItems)
        assertEquals(1.0, m.divertedWeightKg, 0.001)
        assertEquals(1.8, m.co2PreventedKg, 0.001)
    }

    @Test
    fun typoCorrection_fallsBackToOriginalAndSurfacesFlag() {
        val records = listOf(
            WasteRecord(
                id = 1, category = "Recyclable", subclass = "Plastic", confidence = 0.8f,
                estimatedWeight = 1.0, feedback = "incorrect", correctedSubclass = "Plastc-typo-99"
            )
        )
        val m = EcoImpactCalculator.calculate(records)
        // Original Recyclable category used (1.0 * 1.8), item credited, flag surfaced.
        assertEquals(1.8, m.co2PreventedKg, 0.001)
        assertEquals(1, m.divertedItems)
        assertEquals(1, m.fallbackCorrectionItems)
    }

    @Test
    fun hazardousItems_earnBestOfCategoryOrHazardousRate() {
        // Hazardous-category item: hazardous rates.
        val hz = EcoImpactCalculator.calculate(
            listOf(WasteRecord(category = "Hazardous", subclass = "light bulbs", confidence = 0.9f, estimatedWeight = 1.0))
        )
        assertEquals(2.2, hz.co2PreventedKg, 0.001)
        // E-waste battery keeps its HIGHER category rate (3.5 > 2.2), not downgraded.
        val batt = EcoImpactCalculator.calculate(
            listOf(WasteRecord(category = "E-Waste", subclass = "Battery", confidence = 0.95f, estimatedWeight = 1.0))
        )
        assertEquals(3.5, batt.co2PreventedKg, 0.001)
        // Hazardous Trash keeps hazardous credit.
        val trashHz = EcoImpactCalculator.calculate(
            listOf(WasteRecord(category = "Trash", subclass = "light bulbs", confidence = 0.9f, estimatedWeight = 1.0))
        )
        assertEquals(2.2, trashHz.co2PreventedKg, 0.001)
        assertEquals(3.0, trashHz.energySavedKwh, 0.001)
        // Residual (CONTEXT.md name for Trash) behaves identically: plain Residual
        // is weight-only, hazardous Residual keeps hazardous credit.
        val residual = EcoImpactCalculator.calculate(
            listOf(WasteRecord(category = "Residual", subclass = "Miscellaneous Trash", confidence = 0.9f, estimatedWeight = 1.0))
        )
        assertEquals(1.0, residual.totalWeightKg, 0.001)
        assertEquals(0.0, residual.co2PreventedKg, 0.001)
        assertEquals(0, residual.divertedItems)
        val residualHz = EcoImpactCalculator.calculate(
            listOf(WasteRecord(category = "Residual", subclass = "light bulbs", confidence = 0.9f, estimatedWeight = 1.0))
        )
        assertEquals(2.2, residualHz.co2PreventedKg, 0.001)
        assertEquals(1, residualHz.divertedItems)
    }

    @Test
    fun flatOverload_mirrorsRecordPath() {
        val flat = EcoImpactCalculator.calculateFlat(
            categories = listOf("Recyclable", "Organic"),
            subclasses = listOf("Plastic", "Organic"),
            weightsKg = listOf(1.0, 2.0),
            confidences = listOf(0.9f, 0.85f)
        )
        assertEquals(2.8, flat.co2PreventedKg, 0.001) // 1.0*1.8 + 2.0*0.5
        assertEquals(2, flat.divertedItems)
        assertEquals(2, flat.totalItems)
    }

    @Test
    fun flatStrict_rejectsUnequalSizes() {
        assertFailsWith<IllegalArgumentException> {
            EcoImpactCalculator.calculateFlatStrict(
                categories = listOf("Recyclable"),
                subclasses = listOf("Plastic", "Glass"),
                weightsKg = listOf(1.0),
                confidences = listOf(0.9f)
            )
        }
    }

    @Test
    fun accuracy_caseInsensitiveTrimmed() {
        val m = EcoImpactCalculator.calculate(
            listOf(
                WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = 0.9f, feedback = "  Correct "),
                WasteRecord(category = "Organic", subclass = "Organic", confidence = 0.8f, feedback = "INCORRECT", correctedSubclass = "Organic")
            )
        )
        assertEquals(50f, m.accuracyPercentage!!, 0.01f)
    }
}
