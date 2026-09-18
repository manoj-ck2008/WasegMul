package com.agrelius.wasegmul.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached barcode-to-packaging mapping from Open Food Facts or the pre-loaded database.
 *
 * Each entry maps a product barcode (EAN-13/UPC-A) to its packaging material
 * classification in WasegMul's domain taxonomy.
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
)
