package com.agrelius.wasegmul.data.disposal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoCalculator {
    private const val TAG = "GeoCalculator"
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(lat1.isFinite() && lon1.isFinite() && lat2.isFinite() && lon2.isFinite()) {
            "GeoCalculator.distanceKm requires finite coordinates"
        }
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        // Clamp the Haversine `a` into [0,1]: floating-point rounding on antipodal /
        // near-identical points can push it marginally outside, making sqrt(1-a) NaN.
        val a = (sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)).coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}

object DisposalDatabase {
    private const val TAG = "DisposalDatabase"
    private const val ASSET_PATH = "bangalore_disposal_centers.json"

    @Volatile
    private var cachedCenters: List<DisposalCenter>? = null
    private val cacheMutex = Mutex()

    /**
     * Synchronous asset load. MUST be called off the Main thread (it does asset IO
     * + JSON parsing); the ResultScreen caller currently invokes it inside `remember`
     * on Main — migrate that call site to [loadCentersAsync]. Results are cached, so
     * only the first call pays IO. Stream is closed via `use{}`; each row parses in
     * its own `runCatching` so one corrupt row cannot wipe the whole list (§3.40).
     */
    fun loadCenters(context: Context): List<DisposalCenter> {
        cachedCenters?.let { return it }
        val parsed = parseAsset(context)
        cachedCenters = parsed
        return parsed
    }

    /**
     * Suspend IO variant (§3.40): parses on `Dispatchers.IO` with a shared cache.
     * New callers MUST use this instead of [loadCenters].
     */
    suspend fun loadCentersAsync(context: Context): List<DisposalCenter> =
        withContext(Dispatchers.IO) {
            val cached = cachedCenters
            if (cached != null) return@withContext cached
            cacheMutex.withLock {
                cachedCenters?.let { return@withContext it }
                val parsed = parseAsset(context.applicationContext)
                cachedCenters = parsed
                parsed
            }
        }

    /** Test hook: clears the in-memory cache. */
    fun clearCacheForTest() {
        cachedCenters = null
    }

    private fun parseAsset(context: Context): List<DisposalCenter> {
        val centers = mutableListOf<DisposalCenter>()
        var skipped = 0
        try {
            context.assets.open(ASSET_PATH).use { inputStream ->
                val jsonString = InputStreamReader(inputStream).readText()
                val jsonArray = JSONArray(jsonString)

                for (i in 0 until jsonArray.length()) {
                    // Per-item runCatching: one bad row must not kill the whole list.
                    val center = runCatching {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val typeStr = jsonObject.getString("type")
                        // Forward-compat: skip unknown enum values instead of crashing release builds.
                        val type = DisposalCenterType.valueOf(typeStr)

                        val categoriesArray = jsonObject.getJSONArray("acceptedCategories")
                        val acceptedCategories = mutableListOf<String>()
                        for (j in 0 until categoriesArray.length()) {
                            acceptedCategories.add(categoriesArray.getString(j))
                        }

                        DisposalCenter(
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
                    }.getOrElse { e ->
                        skipped++
                        Log.w(TAG, "Skipping corrupt disposal-center row #$i: ${e.message}")
                        null
                    }
                    if (center != null) centers.add(center)
                }
            }
        } catch (e: Exception) {
            // Tagged log (not printStackTrace): distinguishes corrupt-vs-empty asset.
            Log.e(TAG, "Failed to load $ASSET_PATH (returning ${centers.size} parsed, $skipped skipped)", e)
        }
        if (skipped > 0) Log.w(TAG, "Skipped $skipped corrupt row(s) in $ASSET_PATH")
        return centers
    }

    fun findNearest(
        centers: List<DisposalCenter>,
        lat: Double,
        lon: Double,
        category: String,
        limit: Int = 3
    ): List<NearbyCenterMatch> {
        require(limit > 0) { "limit must be positive" }
        // Case-insensitive match via DisposalCenter.accepts (§3.40). Fallback: when no
        // center accepts the category, return the nearest centers regardless rather
        // than an empty list (caller shows them as general drop-off options).
        val matching = centers.filter { center ->
            runCatching { center.accepts(category) }.getOrDefault(false)
        }
        val pool = matching.ifEmpty { centers }
        return pool
            .map { center ->
                val dist = GeoCalculator.distanceKm(lat, lon, center.latitude, center.longitude)
                NearbyCenterMatch(center, dist)
            }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * Civic contacts per category. Matching is case-insensitive with `Trash` →
     * `Residual` compat. Hazardous/Organic return explicit contacts (never an
     * implicit empty list): where no dedicated helpline exists the base civic
     * contacts carry an explanatory description instead of silence (§3.40).
     *
     * All numbers are Bengaluru-specific; see [CivicContact.PHONE_POLICY_NOTE].
     */
    fun getCivicContacts(category: String): List<CivicContact> {
        val contacts = mutableListOf(
            CivicContact("BSWML WhatsApp", "+91 9448197197", "Bengaluru Solid Waste Management Ltd", true),
            CivicContact("GBA Helpline", "1533", "Garbage Block Spot Removal"),
            CivicContact("BBMP Control Room", "080-22660000", "General Civic Complaints")
        )

        when (DisposalCenter.normalizeCategory(category).lowercase()) {
            "recyclable" -> {
                contacts.add(CivicContact("Hasiru Dala", "+91 99868 08866", "Dry Waste Management"))
                contacts.add(CivicContact("Textile Helpline", "+91 97417 30854", "Old Clothes Disposal"))
            }
            "e-waste", "e_waste", "ewaste" -> {
                contacts.add(CivicContact("Saahas Zero Waste", "1800 258 6676", "E-Waste Collection"))
            }
            "hazardous" -> {
                // No dedicated Bengaluru hazardous-waste citizen helpline ships in the
                // asset; route via control room explicitly instead of an empty list.
                contacts.add(
                    CivicContact(
                        "BBMP Hazardous Waste Cell (via Control Room)",
                        "080-22660000",
                        "No dedicated hazardous-waste helpline: report via BBMP Control Room; " +
                            "do NOT bin batteries/chemicals with general waste"
                    )
                )
            }
            "organic" -> {
                contacts.add(
                    CivicContact(
                        "BBMP Compost Helpline (via Control Room)",
                        "080-22660000",
                        "No dedicated compost helpline: home-compost wet waste or report via BBMP Control Room"
                    )
                )
            }
        }

        return contacts
    }
}
