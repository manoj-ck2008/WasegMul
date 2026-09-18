package com.agrelius.wasegmul.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import com.agrelius.wasegmul.PackagingWasteMapper
import com.agrelius.wasegmul.ResolvedPackagingComponent
import com.agrelius.wasegmul.data.BarcodeProduct
import com.agrelius.wasegmul.data.BarcodeProductDao
import com.agrelius.wasegmul.network.OffProductDto
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import com.agrelius.wasegmul.utils.SafeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Result of resolving a barcode through the 4-tier pipeline. */
sealed class BarcodeResolutionResult {
    data class Found(
        val product: BarcodeProduct,
        /**
         * Resolution-source vocabulary (where it was found THIS call):
         * `"preloaded"` (bundled seed) | `"cached"` (stored row: prior API lookup or
         * user-confirmed) | `"api"` (live lookup).
         * Distinct from the STORED [BarcodeProduct.source] vocab
         * (`preloaded` / `api` / `user_identified`, see [BarcodeProduct]).
         */
        val source: String
    ) : BarcodeResolutionResult()

    data object NotFound : BarcodeResolutionResult()

    data class Error(val message: String) : BarcodeResolutionResult()
}

/**
 * Repository orchestrating barcode resolution through the 4-tier Barcode Resolution pipeline
 * (CONTEXT.md): Tier 1 pre-loaded DB → Tier 2 Room cache → Tier 3 live OFF API →
 * Tier 4 caller visual-ML fallback.
 *
 * ### Threading (§3.32)
 * [resolve] (and every DAO/network touch) is confined to `Dispatchers.IO` via
 * [withContext] — the old code ran DAO I/O plus up to ~60 s of sequential network calls
 * on the caller's dispatcher (ANR). Tier-3 runs inside [coroutineScope]+`async` with an
 * overall [withTimeout] ([RESOLVE_TIMEOUT_MS]) plus one transport retry, so a wedged
 * cascade cannot hold CameraX resources open. Tier-3 endpoint fan-out itself
 * (FOOD → PRODUCTS → BEAUTY with per-endpoint isolation + 429/5xx backoff) lives in
 * shared [OpenFoodFactsApi.getProductCascade]; this class adds the deadline and the
 * retry-on-transport-failure around it.
 *
 * ### Input contract
 * Raw scanner output (`FORMAT_ALL_FORMATS` yields spaces/dashes/URLs/QR payloads) MUST
 * go through [sanitizeBarcode]: junk (`code_*` keys, URLs without a GTIN, digit-less
 * payloads) resolves to `null` → [BarcodeResolutionResult.NotFound] + log, never DAO or
 * network. Only ONE null-convention is consumed ([OpenFoodFactsApi.getProductCascade]:
 * `success(null)` or null product = miss; exceptions = transport failure) — the legacy
 * `getProduct` FOOD-only / `queryEndpoint` surfaces are never called from here.
 *
 * ### Matching (§3.31)
 * Keyword fallback uses whole-word regexes ([wordPattern]), not `contains()`: the old
 * `contains("can")` fired on candy/canola/American, `"box"` on Xbox, `"bar"` on barcode.
 *
 * ### Cache contract (§3.39/§3.32)
 * - Barcodes are stored/looked up canonical ([BarcodeProduct.normalizeBarcode]) via the
 *   atomic [BarcodeProductDao.findAndTouch] (no racy find+touch pair).
 * - API rows persist via [BarcodeProductDao.upsertPreservingCachedAt] so refreshes keep
 *   the original `cachedAt` (TTL stays honest) and NEVER overwrite `user_identified`
 *   ground truth with API guesses. Stored source vocab is unified to
 *   `preloaded` / `api` / `user_identified` ([BarcodeProduct.SOURCE_API]; legacy
 *   `"api_cache"` rows still read as cached).
 * - Total misses leave a negative-cache mark ([recordMiss]) with [MISS_TTL_MS] TTL so
 *   repeat scans of an unindexed code don't fan out to 3 databases every time.
 * - [pruneCache] preserves `preloaded` AND `user_identified` rows (the old DAO call
 *   deleted user entries — data loss).
 *
 * ### Category vocabulary
 * New branches use CONTEXT.md `Residual`; the legacy wrapper branch keeps `Trash`
 * (compat — pinned by `BarcodeRepositoryTest`, and runtime history still stores it).
 * [CancellationException] (including Tier-3 timeouts) is always rethrown, never
 * converted to [BarcodeResolutionResult.Error].
 */
class BarcodeRepository(
    private val dao: BarcodeProductDao,
    private val api: OpenFoodFactsApi,
    private val isOnline: () -> Boolean,
    private val context: Context? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var isPreloadChecked = false
    private val preloadMutex = Mutex()

    private val missMutex = Mutex()
    private val missMarks = mutableMapOf<String, Long>()

    /**
     * Seeds the local Room database from the preloaded asset SQLite file
     * if no preloaded items currently exist. Racy-flag + [preloadMutex] guarded;
     * a `null` [context] disables Tier-1 seeding (cache tiers still work) with a log.
     */
    suspend fun ensurePreloaded() = withContext(Dispatchers.IO) {
        if (isPreloadChecked) return@withContext
        preloadMutex.withLock {
            if (isPreloadChecked) return@withLock
            isPreloadChecked = true
            val ctx = context
            if (ctx == null) {
                SafeLog.w(TAG, "ensurePreloaded: null context — Tier-1 seed disabled, cache tiers still active")
                return@withLock
            }
            try {
                if (dao.getPreloadedCount() == 0) {
                    seedFromAsset(ctx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SafeLog.e(TAG, "Failed to check preloaded status", e)
            }
        }
    }

    private suspend fun seedFromAsset(ctx: Context) = withContext(Dispatchers.IO) {
        var sqliteDb: SQLiteDatabase? = null
        val tempFile = File(ctx.cacheDir, "wasegmul_barcode_seed.db")
        try {
            val assetPath = "database/wasegmul_barcode_offline.db"
            ctx.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            sqliteDb = SQLiteDatabase.openDatabase(
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
                    // Normalize defensively: seed variants (`0123…` vs `123…`) must not
                    // create duplicate rows that never hit each other. Blank → skip row.
                    val normalized = BarcodeProduct.normalizeBarcode(c.getString(idxBarcode))
                    if (normalized.isEmpty()) continue
                    products.add(
                        BarcodeProduct(
                            barcode = normalized,
                            productName = if (c.isNull(idxName)) null else c.getString(idxName),
                            brand = if (c.isNull(idxBrand)) null else c.getString(idxBrand),
                            category = c.getString(idxCategory),
                            subclass = c.getString(idxSubclass),
                            materials = if (c.isNull(idxMaterials)) null else c.getString(idxMaterials),
                            componentsJson = if (c.isNull(idxComponents)) null else c.getString(idxComponents),
                            weightGrams = if (c.isNull(idxWeight)) null else c.getDouble(idxWeight),
                            ecoscore = if (c.isNull(idxEcoscore)) null else c.getString(idxEcoscore),
                            source = BarcodeProduct.SOURCE_PRELOADED,
                            packagingsComplete = c.getInt(idxComplete) == 1,
                            lastAccessed = c.getLong(idxLast),
                            cachedAt = c.getLong(idxCached)
                        )
                    )
                }
            }

            if (products.isNotEmpty()) {
                dao.insertAll(products)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to seed preloaded database from assets", e)
        } finally {
            // try/finally (§3.32): the old code leaked the SQLite handle + temp file
            // whenever parsing threw mid-seed.
            runCatching { sqliteDb?.close() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    suspend fun resolve(rawBarcode: String): BarcodeResolutionResult = withContext(Dispatchers.IO) {
        ensurePreloaded()
        val barcode = sanitizeBarcode(rawBarcode)
        if (barcode == null) {
            SafeLog.w(TAG, "resolve: rejecting junk payload pre-lookup (len=${rawBarcode.length})")
            return@withContext BarcodeResolutionResult.NotFound
        }
        if (isFreshMiss(barcode)) {
            return@withContext BarcodeResolutionResult.NotFound
        }

        // Tier 1 + 2: atomic find + lastAccessed touch (no racy read-then-write pair).
        val cached = try {
            dao.findAndTouch(barcode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "resolve: Tier-1/2 lookup failed for $barcode", e)
            return@withContext BarcodeResolutionResult.Error("Local lookup failed")
        }
        if (cached != null) {
            val source = if (cached.source == BarcodeProduct.SOURCE_PRELOADED) "preloaded" else "cached"
            return@withContext BarcodeResolutionResult.Found(cached, source)
        }

        // Tier 3: live OFF cascade (FOOD → PRODUCTS → BEAUTY inside the shared API).
        if (!isOnline()) {
            return@withContext BarcodeResolutionResult.NotFound
        }
        // Checksum-invalid candidates never reach the network inside the cascade, but
        // log here so scanner-calibration issues stay visible.
        if (!OpenFoodFactsApi.isValidGtin(barcode)) {
            SafeLog.w(TAG, "resolve: '$barcode' fails GTIN checksum; cascade will skip network")
        }
        val tier3: Result<BarcodeResolutionResult> = try {
            coroutineScope {
                val deferred = async { cascadeWithRetry(barcode) }
                withTimeout(RESOLVE_TIMEOUT_MS) { deferred.await() }
            }
        } catch (e: CancellationException) {
            // Timeout AND coroutine cancellation propagate — never Error-wrapped.
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "resolve: Tier-3 unexpected failure for $barcode", e)
            Result.failure(e)
        }
        return@withContext tier3.getOrElse { e ->
            BarcodeResolutionResult.Error(
                e.message?.ifBlank { "Lookup failed" } ?: "Lookup failed"
            )
        }
    }

    /**
     * Tier-3 with one transport retry (exponential backoff). Total miss
     * (`success(null)` / null product / 404-ish failure) → [NotFound] + negative-cache
     * mark (no retry — absence is not transient). Other failures retry once, then [Error].
     */
    private suspend fun cascadeWithRetry(barcode: String): Result<BarcodeResolutionResult> {
        var lastError: Throwable? = null
        repeat(TIER3_ATTEMPTS) { attempt ->
            val result = try {
                api.getProductCascade(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                val msg = failure.message ?: ""
                if (msg.contains("404")) {
                    recordMiss(barcode)
                    return Result.success(BarcodeResolutionResult.NotFound)
                }
                lastError = failure
                SafeLog.w(TAG, "resolve: Tier-3 attempt ${attempt + 1}/$TIER3_ATTEMPTS failed: $msg")
            } else {
                val product = result.getOrNull()?.product
                if (product == null) {
                    recordMiss(barcode)
                    return Result.success(BarcodeResolutionResult.NotFound)
                }
                return Result.success(persistApiProduct(barcode, product))
            }
            if (attempt + 1 < TIER3_ATTEMPTS) delay(TIER3_RETRY_BASE_MS shl attempt)
        }
        return Result.failure(lastError ?: IllegalStateException("Lookup failed"))
    }

    private suspend fun persistApiProduct(
        barcode: String,
        product: OffProductDto
    ): BarcodeResolutionResult {
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
        } catch (e: Exception) {
            SafeLog.w(TAG, "resolve: components JSON encode failed; storing null")
            null
        }

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
            source = BarcodeProduct.SOURCE_API,
            packagingsComplete = (product.packagingsComplete ?: 0) == 1
        )
        return try {
            // Versioned upsert: preserves existing cachedAt + never overwrites
            // user_identified ground truth with an API guess.
            dao.upsertPreservingCachedAt(barcodeProduct)
            clearMiss(barcode)
            BarcodeResolutionResult.Found(barcodeProduct, "api")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "resolve: failed to persist API row for $barcode", e)
            BarcodeResolutionResult.Error("Failed to cache lookup result")
        }
    }

    private suspend fun isFreshMiss(barcode: String): Boolean = missMutex.withLock {
        val markedAt = missMarks[barcode] ?: return false
        if (System.currentTimeMillis() - markedAt > MISS_TTL_MS) {
            missMarks.remove(barcode)
            return false
        }
        true
    }

    private suspend fun recordMiss(barcode: String) = missMutex.withLock {
        if (missMarks.size >= MAX_MISS_MARKS) missMarks.clear()
        missMarks[barcode] = System.currentTimeMillis()
    }

    private suspend fun clearMiss(barcode: String) = missMutex.withLock {
        missMarks.remove(barcode)
    }

    /** Test hook: clears negative-cache marks. */
    @VisibleForTesting
    suspend fun clearNegativeCacheForTest() = missMutex.withLock {
        missMarks.clear()
    }

    /**
     * Infers a fallback waste category for non-food products (e.g. electronics, appliances, games)
     * when explicit packaging tags are omitted in the upstream database.
     *
     * Whole-word matching only (see [wordPattern]); weights below are coarse single-unit
     * ESTIMATES for aggregate Eco Impact, never precise measurements (cf. §3.29 — the
     * Tier-4 caller must disclose estimates, never persist guesses as ground truth).
     */
    fun inferPackagingFromProduct(product: BarcodeProduct): BarcodeProduct {
        val combined = "${product.productName.orEmpty()} ${product.brand.orEmpty()}"
        val inferred = matchKeywords(combined)
        return product.copy(
            category = inferred.category,
            subclass = inferred.subclass,
            materials = inferred.material
        )
    }

    private fun inferPackagingFromProduct(product: OffProductDto): ResolvedPackagingComponent {
        return matchKeywords("${product.productName.orEmpty()} ${product.brands.orEmpty()}")
    }

    private fun matchKeywords(combined: String): ResolvedPackagingComponent {
        return when {
            HAZARDOUS_PATTERN.containsMatchIn(combined) -> ResolvedPackagingComponent(
                shape = "Container",
                material = "Hazardous substance",
                category = "Hazardous",
                subclass = "Hazardous Material",
                disposalAction = "Discard",
                // Unknown by construction: household chemical mass varies 1000× by
                // container; null (unknown) instead of a fabricated number.
                weightGrams = null
            )
            ELECTRONICS_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Device",
                    material = "Electronic Waste",
                    category = "E-Waste",
                    subclass = "Electronic Device",
                    disposalAction = "Recycle",
                    weightGrams = 500.0
                )
            }
            APPLIANCE_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Appliance",
                    material = "Metal / Electronics",
                    category = "E-Waste",
                    subclass = "Electronic Device",
                    disposalAction = "Recycle",
                    weightGrams = 5000.0
                )
            }
            CAN_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Can",
                    material = "Aluminium",
                    category = "Recyclable",
                    subclass = "Metal",
                    disposalAction = "Recycle",
                    weightGrams = 15.0
                )
            }
            BOTTLE_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Bottle",
                    material = "PET Plastic",
                    category = "Recyclable",
                    subclass = "Plastic",
                    disposalAction = "Recycle",
                    weightGrams = 25.0
                )
            }
            GLASS_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Jar / Bottle",
                    material = "Glass",
                    category = "Recyclable",
                    subclass = "Glass",
                    disposalAction = "Recycle",
                    weightGrams = 200.0
                )
            }
            CARTON_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Carton",
                    material = "Tetra Pak",
                    category = "Recyclable",
                    subclass = "Cardboard",
                    disposalAction = "Recycle",
                    weightGrams = 30.0
                )
            }
            WRAPPER_PATTERN.containsMatchIn(combined) -> {
                // Legacy branch: keeps `Trash` (compat — pinned by BarcodeRepositoryTest;
                // runtime history still stores `Trash` for curbside general waste).
                ResolvedPackagingComponent(
                    shape = "Wrapper",
                    material = "Plastic Film",
                    category = "Trash",
                    subclass = "Plastic",
                    disposalAction = "Discard",
                    weightGrams = 5.0
                )
            }
            TOILETRY_PATTERN.containsMatchIn(combined) -> {
                ResolvedPackagingComponent(
                    shape = "Bottle",
                    material = "HDPE / PP Plastic",
                    category = "Recyclable",
                    subclass = "Plastic",
                    disposalAction = "Recycle",
                    weightGrams = 40.0
                )
            }
            BOX_PATTERN.containsMatchIn(combined) -> {
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
                // New default: CONTEXT.md `Residual`, NOT optimistic Recyclable —
                // an unrecognized product must not earn recycling credit by default.
                ResolvedPackagingComponent(
                    shape = "Packaging",
                    material = "Mixed materials",
                    category = "Residual",
                    subclass = "General Waste",
                    disposalAction = "Discard",
                    weightGrams = null
                )
            }
        }
    }

    /**
     * Persists a product identified directly by the user in the quick classifier.
     * Normalizes the barcode but NEVER validates/rejects: user ground truth must not
     * be dropped by a checksum gate (e.g. in-store/internal codes).
     */
    suspend fun saveUserIdentifiedProduct(product: BarcodeProduct) = withContext(Dispatchers.IO) {
        val normalized = product.copy(
            barcode = BarcodeProduct.normalizeBarcode(product.barcode),
            source = BarcodeProduct.SOURCE_USER_IDENTIFIED
        )
        if (normalized.barcode.isEmpty()) {
            SafeLog.w(TAG, "saveUserIdentifiedProduct: refusing blank barcode")
            return@withContext
        }
        try {
            // Explicit user write (bypasses the user-preservation guard by design —
            // this IS the user ground truth) + clears any negative-cache mark.
            dao.insertOrReplace(normalized)
            clearMiss(normalized.barcode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "saveUserIdentifiedProduct failed for ${normalized.barcode}", e)
        }
    }

    /** Removes cached entries older than [days] days (preserves pre-loaded AND user entries). */
    suspend fun pruneCache(days: Int = 90) = withContext(Dispatchers.IO) {
        require(days > 0) { "days must be positive (was $days)" }
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        try {
            // Bounded batches (rowid-subselect pages the work); iteration count is
            // derived from cache size so very large caches can't loop forever.
            val iterations = ((dao.getCacheSize() / PRUNE_BATCH) + 1).coerceIn(1, MAX_PRUNE_ITERATIONS)
            repeat(iterations) { dao.pruneOldEntriesPaged(cutoff, PRUNE_BATCH) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "pruneCache failed", e)
        }
    }

    companion object {
        private const val TAG = "BarcodeRepository"

        /** Overall Tier-3 deadline: cascade has 10 s per-request timeouts inside; this bounds the sum. */
        const val RESOLVE_TIMEOUT_MS = 20_000L

        /** Tier-3 transport attempts (initial + 1 retry); misses never retry. */
        const val TIER3_ATTEMPTS = 2
        const val TIER3_RETRY_BASE_MS = 500L

        /** Negative-cache TTL: repeat scans of an unindexed code skip Tier-3 for 6 h. */
        const val MISS_TTL_MS = 6L * 60 * 60 * 1000
        const val MAX_MISS_MARKS = 512

        const val PRUNE_BATCH = 200
        const val MAX_PRUNE_ITERATIONS = 50

        /**
         * Whole-word keyword matcher (`\b…\b`, case-insensitive). Single-token entries
         * carry explicit plural variants (`box|boxes`) because `\bbox\b` must NOT match
         * `Xbox` (and `\bbar\b` must not match `barcode`) — the substring bug of §3.32.
         */
        private fun wordPattern(vararg tokens: String): Regex =
            Regex("\\b(?:${tokens.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

        private val HAZARDOUS_PATTERN = wordPattern(
            "paint", "paints", "pesticide", "pesticides", "insecticide", "insecticides",
            "herbicide", "herbicides", "solvent", "solvents", "asbestos", "mercury", "acid"
        )
        private val ELECTRONICS_PATTERN = wordPattern(
            "laptop", "laptops", "computer", "computers", "smartphone", "smartphones",
            "mobile", "cellphone", "cellphones", "phone", "phones", "tablet", "tablets",
            "charger", "chargers", "cable", "cables", "battery", "batteries",
            "electronic", "electronics", "mouse", "mice", "keyboard", "keyboards",
            "headphone", "headphones", "earbud", "earbuds", "smartwatch"
        )
        private val APPLIANCE_PATTERN = wordPattern(
            "washer", "washers", "washing", "dryer", "dryers", "fridge", "fridges",
            "refrigerator", "refrigerators", "microwave", "microwaves", "appliance", "appliances"
        )
        private val CAN_PATTERN = wordPattern(
            "can", "cans", "aluminium", "aluminum", "tin", "tins", "soda can"
        )
        private val BOTTLE_PATTERN = wordPattern(
            "water", "bottle", "bottles", "cola", "coke", "pepsi", "sprite", "fanta",
            "drink", "drinks", "beverage", "beverages", "juice"
        )
        private val GLASS_PATTERN = wordPattern("glass", "wine", "beer", "jar", "jars")
        private val CARTON_PATTERN = wordPattern("milk", "tetra", "carton", "cartons")
        private val WRAPPER_PATTERN = wordPattern(
            "chips", "crisps", "snack", "snacks", "biscuit", "biscuits",
            "cookie", "cookies", "candy", "candies", "chocolate", "chocolates",
            "wrapper", "wrappers", "pouch", "pouches", "bar", "bars"
        )
        private val TOILETRY_PATTERN = wordPattern(
            "shampoo", "soap", "lotion", "lotions", "detergent", "detergents",
            "cleaner", "cleaners", "cosmetic", "cosmetics"
        )
        private val BOX_PATTERN = wordPattern(
            "uno", "card", "cards", "board game", "box", "boxes", "book", "books", "paper"
        )

        private val URL_PREFIX = Regex("^https?://", RegexOption.IGNORE_CASE)
        private val GS1_IN_URL = Regex("""(?:/(?:01|gtin|product)/|\(01\)|\?|&gtin=)(\d{8,14})""")
        private val SLASHLESS_GS1 = Regex("""(?:^|[/&?])01(\d{14})(?:[/&?]|$)""")

        /**
         * Junk rejection pre-resolve (§3.32/§3.36): blanks, `code_<timestamp>` fabricated
         * keys, non-GTIN URLs, and digit-less payloads must never reach Tier-1/3 or Room.
         */
        fun isJunkBarcode(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return true
            if (trimmed.startsWith("code_")) return true
            if (!trimmed.any { it.isDigit() }) return true
            if (URL_PREFIX.containsMatchIn(trimmed)) {
                return extractGtinFromPayload(trimmed) == null
            }
            return false
        }

        private fun extractGtinFromPayload(trimmed: String): String? {
            GS1_IN_URL.find(trimmed)?.let { return it.groupValues[1] }
            SLASHLESS_GS1.find(trimmed)?.let { return it.groupValues[1] }
            return null
        }

        /**
         * Cleans raw scanner output to canonical storage form, or `null` when the
         * payload is junk (see [isJunkBarcode]).
         *
         * - trims / strips spaces + dashes (scanner `FORMAT_ALL_FORMATS` artifacts);
         * - extracts the GTIN from GS1/URL payloads (QR codes often encode links);
         * - 12-digit UPC-A → zero-pads to 13-digit GTIN ([BarcodeProduct.normalizeBarcode]);
         * - requires all-digits with a GS1 length (8/12/13/14). Checksum validity is
         *   NOT required here — Tier-1 cache still answers known rows and the Tier-3
         *   cascade skips checksum-invalid candidates without network calls.
         */
        fun sanitizeBarcode(raw: String): String? {
            val trimmed = raw.trim()
            if (isJunkBarcode(trimmed)) return null
            val payload = if (URL_PREFIX.containsMatchIn(trimmed)) {
                extractGtinFromPayload(trimmed) ?: return null
            } else {
                trimmed
            }
            val normalized = BarcodeProduct.normalizeBarcode(payload)
            if (normalized.isEmpty() || !normalized.all { it.isDigit() }) return null
            if (normalized.length !in setOf(8, 12, 13, 14)) return null
            return normalized
        }
    }
}
