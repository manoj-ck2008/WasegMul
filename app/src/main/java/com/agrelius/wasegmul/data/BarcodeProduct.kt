package com.agrelius.wasegmul.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached barcode-to-packaging mapping from Open Food Facts or the pre-loaded database.
 *
 * Each entry maps a product barcode (EAN-13/UPC-A) to its packaging material
 * classification in WasegMul's domain taxonomy.
 *
 * ### Barcode normalization (§3.39)
 * [barcode] is ALWAYS stored in canonical form (see [normalizeBarcode]): trimmed,
 * spaces/dashes stripped, 12-digit UPC-A left-padded to 13 digits. Variants
 * (`"0123…"`, `"123…"`, `"123-…"`) would otherwise create duplicate rows that never
 * hit each other. Writers MUST normalize before insert/query ([BarcodeProductDao]
 * upserts and [repository.BarcodeRepository] both enforce this).
 *
 * ### Timestamps
 * [lastAccessed]/[cachedAt] default to `now` for Room convenience, but tests and
 * seed paths MUST pass explicit values for determinism (defaults make prune/TTL
 * assertions time-dependent).
 *
 * ### TTL / version policy (documented, no column)
 * No `ttlDays`/`schemaVersion` column is added: that would require a table rebuild
 * migration for bookkeeping the repository already enforces in code
 * ([BarcodeProductDao.pruneOldEntries] + `CACHE_TTL_DAYS` in the repository).
 * If per-row TTLs are ever needed, add the column with a migration then.
 */
@Entity(
    tableName = "barcode_products",
    indices = [
        Index("category"),
        Index("lastAccessed")
    ]
)
data class BarcodeProduct(
    @PrimaryKey val barcode: String,
    val productName: String?,
    val brand: String?,
    val category: String,
    val subclass: String,
    val materials: String?,
    val componentsJson: String?,
    val weightGrams: Double?,
    val ecoscore: String?,
    val source: String,
    val packagingsComplete: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis(),
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Stored source for the bundled offline seed database (never pruned). */
        const val SOURCE_PRELOADED = "preloaded"

        /** Stored source for live Open Food Facts lookups (unified vocab, see below). */
        const val SOURCE_API = "api"

        /**
         * Legacy stored source written by earlier builds (`"api_cache"`). Still
         * treated as a regular API row on read; writers must use [SOURCE_API].
         * Stored-result vocab mapping: `preloaded → preloaded`, anything else → cached/api.
         */
        const val SOURCE_API_CACHE_LEGACY = "api_cache"

        /** Stored source for user-confirmed entries (never pruned, never overwritten). */
        const val SOURCE_USER_IDENTIFIED = "user_identified"

        /** Sources exempt from cache pruning. */
        val PRUNE_EXEMPT_SOURCES = setOf(SOURCE_PRELOADED, SOURCE_USER_IDENTIFIED)

        /**
         * Canonicalizes a raw barcode for storage/lookup (§3.39).
         *
         * - trims surrounding whitespace; strips inner spaces and dashes
         *   (scanner `FORMAT_ALL_FORMATS` yields `"123-456"`, QR payloads embed spaces);
         * - 12-digit UPC-A → left-pads one `0` to 13-digit GTIN
         *   (GS1: UPC-A is GTIN-12, zero-padded to GTIN-13; checksum digit preserved);
         * - 8-digit EAN-8 and 14-digit GTIN are passed through unchanged (callers
         *   handle GTIN-14 → GTIN-13 resolution, not storage).
         *
         * Returns `""` for blank input (callers must reject before Tier-1 lookup).
         */
        fun normalizeBarcode(raw: String): String {
            val stripped = raw.trim().replace(" ", "").replace("-", "")
            if (stripped.isEmpty()) return ""
            // Keep only GTIN-plausible numerics; non-numeric payloads (URLs, `code_*`
            // junk) are the caller's job to extract/reject pre-resolve.
            if (!stripped.all { it.isDigit() }) return stripped
            return if (stripped.length == 12) "0$stripped" else stripped
        }

        /** `true` when [barcode] is already in canonical storage form. */
        fun isNormalized(barcode: String): Boolean =
            barcode.isNotEmpty() && normalizeBarcode(barcode) == barcode
    }
}
