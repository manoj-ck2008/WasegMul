package com.agrelius.wasegmul.data.disposal

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoCalculator {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}

object DisposalDatabase {

    fun loadCenters(context: Context): List<DisposalCenter> {
        val centers = mutableListOf<DisposalCenter>()
        try {
            val inputStream = context.assets.open("bangalore_disposal_centers.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val typeStr = jsonObject.getString("type")
                val type = DisposalCenterType.valueOf(typeStr)
                
                val categoriesArray = jsonObject.getJSONArray("acceptedCategories")
                val acceptedCategories = mutableListOf<String>()
                for (j in 0 until categoriesArray.length()) {
                    acceptedCategories.add(categoriesArray.getString(j))
                }

                val center = DisposalCenter(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    type = type,
                    zone = jsonObject.getString("zone"),
                    address = jsonObject.getString("address"),
                    latitude = jsonObject.getDouble("latitude"),
                    longitude = jsonObject.getDouble("longitude"),
                    phone = if (jsonObject.isNull("phone")) null else jsonObject.getString("phone"),
                    operatingHours = jsonObject.getString("operatingHours"),
                    acceptedCategories = acceptedCategories,
                    description = jsonObject.optString("description", "")
                )
                centers.add(center)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return centers
    }

    fun findNearest(
        centers: List<DisposalCenter>,
        lat: Double,
        lon: Double,
        category: String,
        limit: Int = 3
    ): List<NearbyCenterMatch> {
        return centers
            .filter { category in it.acceptedCategories }
            .map { center ->
                val dist = GeoCalculator.distanceKm(lat, lon, center.latitude, center.longitude)
                NearbyCenterMatch(center, dist)
            }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    fun getCivicContacts(category: String): List<CivicContact> {
        val contacts = mutableListOf(
            CivicContact("BSWML WhatsApp", "+91 9448197197", "Bengaluru Solid Waste Management Ltd", true),
            CivicContact("GBA Helpline", "1533", "Garbage Block Spot Removal"),
            CivicContact("BBMP Control Room", "080-22660000", "General Civic Complaints")
        )

        when (category) {
            "Recyclable" -> {
                contacts.add(CivicContact("Hasiru Dala", "+91 99868 08866", "Dry Waste Management"))
                contacts.add(CivicContact("Textile Helpline", "+91 97417 30854", "Old Clothes Disposal"))
            }
            "E-Waste" -> {
                contacts.add(CivicContact("Saahas Zero Waste", "1800 258 6676", "E-Waste Collection"))
            }
        }
        
        return contacts
    }
}
