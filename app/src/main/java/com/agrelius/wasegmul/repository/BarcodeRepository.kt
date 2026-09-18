package com.agrelius.wasegmul.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.agrelius.wasegmul.PackagingWasteMapper
import com.agrelius.wasegmul.ResolvedPackagingComponent
import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.data.BarcodeProductDao
import com.agrelius.wasegmul.network.OffProductDto
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Result of resolving a barcode through the 4-tier pipeline. */
sealed class BarcodeResolutionResult {
    data class Found(
        val product: BarcodeProduct,
        val source: String   // "preloaded", "cached", or "api"
    ) : BarcodeResolutionResult()

    data object NotFound : BarcodeResolutionResult()

    data class Error(val message: String) : BarcodeResolutionResult()
}

/**
 * Repository orchestrating barcode resolution through the 4-tier pipeline:
 * Tier 1: Pre-loaded local database
 * Tier 2: Previously cached API entries in Room
 * Tier 3: Live Open Food Facts API lookup
 * Tier 4: Caller fallback to visual ML classification
 */
class BarcodeRepository(
    private val dao: BarcodeProductDao,
    private val api: OpenFoodFactsApi,
    private val isOnline: () -> Boolean,
    private val context: Context? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var isPreloadChecked = false

    /**
     * Seeds the local Room database from the preloaded asset SQLite file
     * if no preloaded items currently exist.
     */
    suspend fun ensurePreloaded() {
        if (isPreloadChecked || context == null) return
        isPreloadChecked = true
        try {
            if (dao.getPreloadedCount() == 0) {
                seedFromAsset(context)
            }
        } catch (e: Exception) {
            android.util.Log.e("BarcodeRepository", "Failed to check preloaded status", e)
        }
    }

    private suspend fun seedFromAsset(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            val assetManager = ctx.assets
            val assetPath = "database/wasegmul_barcode_offline.db"
            val tempFile = File(ctx.cacheDir, "wasegmul_barcode_seed.db")
            assetManager.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val sqliteDb = SQLiteDatabase.openDatabase(
                tempFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val products = mutableListOf<BarcodeProduct>()
            val cursor = sqliteDb.rawQuery(
                "SELECT barcode, productName, brand, category, subclass, materials, " +
                    "componentsJson, weightGrams, ecoscore, source, packagingsComplete, " +
                    "lastAccessed, cachedAt FROM barcode_products",
                null
            )
            cursor.use { c ->
                val idxBarcode = c.getColumnIndexOrThrow("barcode")
                val idxName = c.getColumnIndexOrThrow("productName")
                val idxBrand = c.getColumnIndexOrThrow("brand")
                val idxCategory = c.getColumnIndexOrThrow("category")
                val idxSubclass = c.getColumnIndexOrThrow("subclass")
                val idxMaterials = c.getColumnIndexOrThrow("materials")
                val idxComponents = c.getColumnIndexOrThrow("componentsJson")
                val idxWeight = c.getColumnIndexOrThrow("weightGrams")
                val idxEcoscore = c.getColumnIndexOrThrow("ecoscore")
                val idxComplete = c.getColumnIndexOrThrow("packagingsComplete")
                val idxLast = c.getColumnIndexOrThrow("lastAccessed")
                val idxCached = c.getColumnIndexOrThrow("cachedAt")

                while (c.moveToNext()) {
                    products.add(
                        BarcodeProduct(
                            barcode = c.getString(idxBarcode),
                            productName = if (c.isNull(idxName)) null else c.getString(idxName),
                            brand = if (c.isNull(idxBrand)) null else c.getString(idxBrand),
                            category = c.getString(idxCategory),
                            subclass = c.getString(idxSubclass),
                            materials = if (c.isNull(idxMaterials)) null else c.getString(idxMaterials),
                            componentsJson = if (c.isNull(idxComponents)) null else c.getString(idxComponents),
                            weightGrams = if (c.isNull(idxWeight)) null else c.getDouble(idxWeight),
                            ecoscore = if (c.isNull(idxEcoscore)) null else c.getString(idxEcoscore),
                            source = "preloaded",
                            packagingsComplete = c.getInt(idxComplete) == 1,
                            lastAccessed = c.getLong(idxLast),
                            cachedAt = c.getLong(idxCached)
                        )
                    )
                }
            }
            sqliteDb.close()
            tempFile.delete()

            if (products.isNotEmpty()) {
                dao.insertAll(products)
            }
        } catch (e: Exception) {
            android.util.Log.e("BarcodeRepository", "Failed to seed preloaded database from assets", e)
        }
    }

    suspend fun resolve(barcode: String): BarcodeResolutionResult {
        ensurePreloaded()
        // Tier 1 + 2: Check local DB (pre-loaded + previously cached)
        val cached = dao.findByBarcode(barcode) ?: if (barcode.length == 12) dao.findByBarcode("0$barcode") else null
        cached?.let {
            dao.updateLastAccessed(it.barcode)
            val source = if (it.source == "preloaded") "preloaded" else "cached"
            return BarcodeResolutionResult.Found(it, source)
        }

        // Tier 3: Online multi-database API lookup (Food -> Products -> Beauty)
        if (isOnline()) {
            val result = api.getProductCascade(barcode)
            result.onSuccess { response ->
                val product = response?.product ?: return BarcodeResolutionResult.NotFound
                val primary = PackagingWasteMapper.resolvePrimaryComponent(product)
                    ?: inferPackagingFromProduct(product)
                val components = if (product.packagings.isNotEmpty() || product.materialsTags.isNotEmpty()) {
                    PackagingWasteMapper.resolveComponents(product)
                } else {
                    listOf(primary)
                }
                val componentsJsonStr = try {
                    if (product.packagings.isNotEmpty()) json.encodeToString(product.packagings)
                    else null
                } catch (e: Exception) { null }

                val barcodeProduct = BarcodeProduct(
                    barcode = barcode,
                    productName = product.productName,
                    brand = product.brands,
                    category = primary.category,
                    subclass = primary.subclass,
                    materials = components.joinToString(", ") { it.material },
                    componentsJson = componentsJsonStr,
                    weightGrams = primary.weightGrams,
                    ecoscore = product.ecoscoreGrade,
                    source = "api_cache",
                    packagingsComplete = (product.packagingsComplete ?: 0) == 1
                )
                dao.insertOrReplace(barcodeProduct)
                return BarcodeResolutionResult.Found(barcodeProduct, "api")
            }
            result.onFailure { e ->
                // Cleanly return NotFound if not found rather than blocking error
                val msg = e.message ?: ""
                if (msg.contains("404")) {
                    return BarcodeResolutionResult.NotFound
                }
                return BarcodeResolutionResult.Error(msg.ifBlank { "Lookup failed" })
            }
        }

        // Tier 4: No data, caller falls back to quick classifier or visual ML
        return BarcodeResolutionResult.NotFound
    }

    /**
     * Infers a fallback waste category for non-food products (e.g. electronics, appliances, games)
     * when explicit packaging tags are omitted in the upstream database.
     */
    fun inferPackagingFromProduct(product: BarcodeProduct): BarcodeProduct {
        val name = (product.productName ?: "").lowercase()
        val brand = (product.brand ?: "").lowercase()
        val combined = "$name $brand"
        val inferred = inferPackagingFromKeywords(combined)
        return product.copy(
            category = inferred.category,
            subclass = inferred.subclass,
            materials = inferred.material
        )
    }

    private fun inferPackagingFromProduct(product: OffProductDto): ResolvedPackagingComponent {
        val name = (product.productName ?: "").lowercase()
        val brands = (product.brands ?: "").lowercase()
        return inferPackagingFromKeywords("$name $brands")
    }

    private fun inferPackagingFromKeywords(combined: String): ResolvedPackagingComponent {
        return when {
            combined.contains("laptop") || combined.contains("computer") ||
            combined.contains("phone") || combined.contains("tablet") ||
            combined.contains("charger") || combined.contains("cable") ||
            combined.contains("battery") || combined.contains("electronics") ||
            combined.contains("mouse") || combined.contains("keyboard") ||
            combined.contains("headphone") || combined.contains("earbud") -> {
                ResolvedPackagingComponent(
                    shape = "Device",
                    material = "Electronic Waste",
                    category = "E-Waste",
                    subclass = "Electronic Device",
                    disposalAction = "Recycle",
                    weightGrams = 500.0
                )
            }
            combined.contains("washer") || combined.contains("washing") ||
            combined.contains("dryer") || combined.contains("fridge") ||
            combined.contains("refrigerator") || combined.contains("microwave") ||
            combined.contains("appliance") -> {
                ResolvedPackagingComponent(
                    shape = "Appliance",
                    material = "Metal / Electronics",
                    category = "E-Waste",
                    subclass = "Electronic Device",
                    disposalAction = "Recycle",
                    weightGrams = 5000.0
                )
            }
            combined.contains("can") || combined.contains("aluminium") ||
            combined.contains("tin") || combined.contains("soda can") -> {
                ResolvedPackagingComponent(
                    shape = "Can",
                    material = "Aluminium",
                    category = "Recyclable",
                    subclass = "Metal",
                    disposalAction = "Recycle",
                    weightGrams = 15.0
                )
            }
            combined.contains("water") || combined.contains("bottle") ||
            combined.contains("cola") || combined.contains("coke") ||
            combined.contains("pepsi") || combined.contains("sprite") ||
            combined.contains("fanta") || combined.contains("drink") ||
            combined.contains("beverage") || combined.contains("juice") -> {
                ResolvedPackagingComponent(
                    shape = "Bottle",
                    material = "PET Plastic",
                    category = "Recyclable",
                    subclass = "Plastic",
                    disposalAction = "Recycle",
                    weightGrams = 25.0
                )
            }
            combined.contains("glass") || combined.contains("wine") ||
            combined.contains("beer") || combined.contains("jar") -> {
                ResolvedPackagingComponent(
                    shape = "Jar / Bottle",
                    material = "Glass",
                    category = "Recyclable",
                    subclass = "Glass",
                    disposalAction = "Recycle",
                    weightGrams = 200.0
                )
            }
            combined.contains("milk") || combined.contains("tetra") ||
            combined.contains("carton") -> {
                ResolvedPackagingComponent(
                    shape = "Carton",
                    material = "Tetra Pak",
                    category = "Recyclable",
                    subclass = "Cardboard",
                    disposalAction = "Recycle",
                    weightGrams = 30.0
                )
            }
            combined.contains("chips") || combined.contains("crisps") ||
            combined.contains("snack") || combined.contains("biscuit") ||
            combined.contains("cookie") || combined.contains("candy") ||
            combined.contains("chocolate") || combined.contains("wrapper") ||
            combined.contains("pouch") || combined.contains("bar") -> {
                ResolvedPackagingComponent(
                    shape = "Wrapper",
                    material = "Plastic Film",
                    category = "Trash",
                    subclass = "Plastic",
                    disposalAction = "Discard",
                    weightGrams = 5.0
                )
            }
            combined.contains("shampoo") || combined.contains("soap") ||
            combined.contains("lotion") || combined.contains("detergent") ||
            combined.contains("cleaner") || combined.contains("cosmetic") -> {
                ResolvedPackagingComponent(
                    shape = "Bottle",
                    material = "HDPE / PP Plastic",
                    category = "Recyclable",
                    subclass = "Plastic",
                    disposalAction = "Recycle",
                    weightGrams = 40.0
                )
            }
            combined.contains("uno") || combined.contains("card") ||
            combined.contains("board game") || combined.contains("box") ||
            combined.contains("book") || combined.contains("paper") -> {
                ResolvedPackagingComponent(
                    shape = "Box",
                    material = "Cardboard",
                    category = "Recyclable",
                    subclass = "Cardboard",
                    disposalAction = "Recycle",
                    weightGrams = 50.0
                )
            }
            else -> {
                ResolvedPackagingComponent(
                    shape = "Packaging",
                    material = "Cardboard / Plastic",
                    category = "Recyclable",
                    subclass = "Cardboard",
                    disposalAction = "Recycle",
                    weightGrams = 50.0
                )
            }
        }
    }

    /**
     * Persists a product identified directly by the user in the quick classifier.
     */
    suspend fun saveUserIdentifiedProduct(product: BarcodeProduct) {
        dao.insertOrReplace(product.copy(source = "user_identified"))
    }

    /** Removes cached entries older than [days] days (preserves pre-loaded entries). */
    suspend fun pruneCache(days: Int = 90) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        dao.pruneOldEntries(cutoff)
    }
}
