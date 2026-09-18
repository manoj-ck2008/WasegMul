package com.agrelius.wasegmul.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the barcode products cache table.
 *
 * All [barcode] arguments MUST be canonical ([BarcodeProduct.normalizeBarcode]);
 * the transactional helpers below normalize defensively, but callers normalize first.
 */
@Dao
interface BarcodeProductDao {
    @Query("SELECT * FROM barcode_products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): BarcodeProduct?

    /**
     * Atomic find + touch (§3.39): replaces the racy `findByBarcode` +
     * `updateLastAccessed` pair. Returns the row (or null) after refreshing
     * `lastAccessed`, so concurrent resolvers cannot interleave a stale read.
     */
    @Transaction
    suspend fun findAndTouch(barcode: String, now: Long = System.currentTimeMillis()): BarcodeProduct? {
        val normalized = BarcodeProduct.normalizeBarcode(barcode)
        val row = findByBarcode(normalized) ?: return null
        updateLastAccessed(normalized, now)
        return row.copy(lastAccessed = now)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(product: BarcodeProduct)

    /**
     * Versioned upsert (§3.39): inserts, or replaces while PRESERVING the existing
     * `cachedAt` (plain `REPLACE` reset it, making every refresh look brand-new and
     * defeating TTL pruning). `user_identified` rows are never overwritten here —
     * use [insertOrReplace] explicitly for user confirmations.
     */
    @Transaction
    suspend fun upsertPreservingCachedAt(product: BarcodeProduct) {
        val normalized = product.copy(barcode = BarcodeProduct.normalizeBarcode(product.barcode))
        val existing = findByBarcode(normalized.barcode)
        if (existing == null) {
            insertOrReplace(normalized)
        } else if (existing.source == BarcodeProduct.SOURCE_USER_IDENTIFIED) {
            // Preserve user ground truth: only refresh lastAccessed.
            updateLastAccessed(normalized.barcode, normalized.lastAccessed)
        } else {
            insertOrReplace(normalized.copy(cachedAt = existing.cachedAt))
        }
    }

    @Query("UPDATE barcode_products SET lastAccessed = :now WHERE barcode = :barcode")
    suspend fun updateLastAccessed(barcode: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM barcode_products ORDER BY lastAccessed DESC LIMIT :limit")
    fun getRecentScans(limit: Int = 20): Flow<List<BarcodeProduct>>

    @Query("SELECT COUNT(*) FROM barcode_products")
    suspend fun getCacheSize(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(products: List<BarcodeProduct>)

    @Query("SELECT COUNT(*) FROM barcode_products WHERE source = 'preloaded'")
    suspend fun getPreloadedCount(): Int

    /**
     * Prunes stale API rows (§3.39). `preloaded` AND `user_identified` rows are
     * preserved (earlier builds deleted user entries — data loss).
     */
    @Query(
        "DELETE FROM barcode_products WHERE cachedAt < :cutoff " +
            "AND source NOT IN ('preloaded', 'user_identified')"
    )
    suspend fun pruneOldEntries(cutoff: Long)

    /**
     * Bounded prune batch for very large caches: deletes at most [limit] stale rows
     * per call (SQLite has no `DELETE … LIMIT`; rowid-subselect pages the work).
     * Loop until it returns 0.
     */
    @Query(
        "DELETE FROM barcode_products WHERE rowid IN (" +
            "SELECT rowid FROM barcode_products WHERE cachedAt < :cutoff " +
            "AND source NOT IN ('preloaded', 'user_identified') LIMIT :limit)"
    )
    suspend fun pruneOldEntriesPaged(cutoff: Long, limit: Int)
}
