package com.agrelius.wasegmul.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database holding the classification history.
 *
 * Version history:
 *  - v1..v6: early development (destructive fallback only).
 *  - v7: added `topPredictions` column. A real migration is provided so existing user
 *        history is preserved rather than wiped.
 *  - v8: added `timestamp` index for fast history queries.
 *  - v9: added `category`, `subclass`, and `feedback` indices for fast filtering.
 *  - v10: added `barcode_products` table and indices for barcode packaging cache.
 *
 * NOTE: `fallbackToDestructiveMigration()` IS enabled as a last-resort safety net so a
 * future schema change or an old v1..v5 install never crashes on launch. Every intentional
 * schema change MUST still ship with an explicit [Migration] entry below; destructive
 * fallback only triggers when no migration path exists (documented wipe, not crash).
 */
@Database(
    entities = [WasteRecord::class, BarcodeProduct::class],
    version = 10,
    exportSchema = true
)
abstract class WasteDatabase : RoomDatabase() {

    abstract fun wasteDao(): WasteDao
    abstract fun barcodeProductDao(): BarcodeProductDao

    companion object {
        @Volatile
        private var INSTANCE: WasteDatabase? = null

        /** v6 -> v7: persist the top-K subclass predictions alongside each record. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE waste_history ADD COLUMN topPredictions TEXT")
            }
        }

        /** v7 -> v8: add index on timestamp for fast ORDER BY timestamp queries. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_timestamp ON waste_history (timestamp)")
            }
        }

        /** v8 -> v9: add indices on category, subclass, and feedback for fast filtering. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_category ON waste_history (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_subclass ON waste_history (subclass)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_waste_history_feedback ON waste_history (feedback)")
            }
        }

        /** v9 -> v10: add barcode_products table and indices for barcode caching. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS barcode_products (
                        barcode TEXT NOT NULL PRIMARY KEY,
                        productName TEXT,
                        brand TEXT,
                        category TEXT NOT NULL,
                        subclass TEXT NOT NULL,
                        materials TEXT,
                        componentsJson TEXT,
                        weightGrams REAL,
                        ecoscore TEXT,
                        source TEXT NOT NULL,
                        packagingsComplete INTEGER NOT NULL DEFAULT 0,
                        lastAccessed INTEGER NOT NULL DEFAULT 0,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_barcode_products_category ON barcode_products (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_barcode_products_lastAccessed ON barcode_products (lastAccessed)")
            }
        }

        fun getDatabase(context: Context): WasteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WasteDatabase::class.java,
                    "wasegmul_industrial_v1.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
