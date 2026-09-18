package com.agrelius.wasegmul.data.disposal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DisposalDatabaseTest {

    @Test
    fun testGeoCalculatorDistance() {
        // Distance between Bangalore Vidhana Soudha (12.9797, 77.5908) and Indiranagar (12.9716, 77.6413) ~5.5 km
        val distance = GeoCalculator.distanceKm(12.9797, 77.5908, 12.9716, 77.6413)
        assertTrue("Distance should be around 5.5km, was $distance", distance in 5.0..6.0)

        // Same point distance is 0
        val zeroDist = GeoCalculator.distanceKm(12.9716, 77.5946, 12.9716, 77.5946)
        assertEquals(0.0, zeroDist, 0.001)
    }

    @Test
    fun testFindNearestFiltersAndSorts() {
        val center1 = DisposalCenter(
            id = "dwcc_1",
            name = "Far DWCC",
            type = DisposalCenterType.DWCC,
            zone = "East",
            address = "Far Address",
            latitude = 13.1000,
            longitude = 77.7000,
            phone = null,
            operatingHours = "9-5",
            acceptedCategories = listOf("Recyclable")
        )

        val center2 = DisposalCenter(
            id = "dwcc_2",
            name = "Near DWCC",
            type = DisposalCenterType.DWCC,
            zone = "East",
            address = "Near Address",
            latitude = 12.9720,
            longitude = 77.6420,
            phone = null,
            operatingHours = "9-5",
            acceptedCategories = listOf("Recyclable")
        )

        val center3 = DisposalCenter(
            id = "ewaste_1",
            name = "Near E-Waste",
            type = DisposalCenterType.E_WASTE,
            zone = "East",
            address = "E-Waste Address",
            latitude = 12.9718,
            longitude = 77.6415,
            phone = null,
            operatingHours = "9-5",
            acceptedCategories = listOf("E-Waste")
        )

        val allCenters = listOf(center1, center2, center3)

        // Query Recyclable near Indiranagar
        val results = DisposalDatabase.findNearest(
            centers = allCenters,
            lat = 12.9716,
            lon = 77.6413,
            category = "Recyclable",
            limit = 5
        )

        assertEquals(2, results.size)
        assertEquals("dwcc_2", results[0].center.id)
        assertEquals("dwcc_1", results[1].center.id)
        assertTrue(results[0].distanceKm < results[1].distanceKm)
    }

    @Test
    fun testCivicContacts() {
        val ewasteContacts = DisposalDatabase.getCivicContacts("E-Waste")
        assertTrue(ewasteContacts.any { it.name.contains("Saahas") })
        assertTrue(ewasteContacts.any { it.number == "1533" })

        val recyclableContacts = DisposalDatabase.getCivicContacts("Recyclable")
        assertTrue(recyclableContacts.any { it.name.contains("Hasiru Dala") })
    }

    @Test
    fun accepts_isCaseInsensitive_withTrashCompat() {
        val center = DisposalCenter(
            id = "dwcc_x",
            name = "X",
            type = DisposalCenterType.DWCC,
            zone = "East",
            address = "Addr",
            latitude = 12.97,
            longitude = 77.64,
            phone = null,
            operatingHours = "9-5",
            acceptedCategories = listOf("recyclable")
        )
        assertTrue(center.accepts("Recyclable"))
        assertTrue(center.accepts(" RECYCLABLE "))
        assertFalse(center.accepts("E-Waste"))

        val residual = center.copy(id = "dwcc_y", acceptedCategories = listOf("Residual"))
        assertTrue(residual.accepts("Trash"))
        assertTrue(residual.accepts("residual"))
    }

    @Test
    fun constructor_rejectsBadCoordinates() {
        try {
            DisposalCenter(
                id = "bad", name = "Bad", type = DisposalCenterType.DWCC, zone = "E",
                address = "A", latitude = 91.0, longitude = 77.0, phone = null,
                operatingHours = "9-5", acceptedCategories = listOf("Recyclable")
            )
            fail("expected IllegalArgumentException for latitude=91")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            DisposalCenter(
                id = "bad", name = "Bad", type = DisposalCenterType.DWCC, zone = "E",
                address = "A", latitude = 12.0, longitude = Double.NaN, phone = null,
                operatingHours = "9-5", acceptedCategories = listOf("Recyclable")
            )
            fail("expected IllegalArgumentException for NaN longitude")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun haversine_antipodal_isFiniteHalfCircumference() {
        val d = GeoCalculator.distanceKm(0.0, 0.0, 0.0, 180.0)
        assertTrue("antipodal distance must be finite, was $d", d.isFinite())
        assertEquals(20015.0, d, 50.0)
    }

    @Test
    fun findNearest_fallsBackToNearest_whenNoCategoryMatch() {
        val center = DisposalCenter(
            id = "dwcc_1",
            name = "Only DWCC",
            type = DisposalCenterType.DWCC,
            zone = "East",
            address = "Addr",
            latitude = 12.9720,
            longitude = 77.6420,
            phone = null,
            operatingHours = "9-5",
            acceptedCategories = listOf("Recyclable")
        )
        // No center accepts Hazardous: returns nearest regardless, not empty.
        val results = DisposalDatabase.findNearest(
            centers = listOf(center),
            lat = 12.9716,
            lon = 77.6413,
            category = "Hazardous",
            limit = 3
        )
        assertEquals(1, results.size)
        assertEquals("dwcc_1", results[0].center.id)
    }

    @Test
    fun civicContacts_hazardousAndOrganic_areExplicit() {
        val hazardous = DisposalDatabase.getCivicContacts("hazardous")
        assertTrue(hazardous.any { it.name.contains("Hazardous") })
        val organic = DisposalDatabase.getCivicContacts("ORGANIC")
        assertTrue(organic.any { it.name.contains("Compost") })
    }
}
