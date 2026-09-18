package com.agrelius.wasegmul.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the barcode products cache table.
 */
@Dao
interface BarcodeProductDao {
    @Query("SELECT * FROM barcode_products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): BarcodeProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(product: BarcodeProduct)

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

    @Query("DELETE FROM barcode_products WHERE cachedAt < :cutoff AND source != 'preloaded'")
    suspend fun pruneOldEntries(cutoff: Long)
}
