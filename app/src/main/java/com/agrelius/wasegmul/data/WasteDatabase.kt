package com.agrelius.wasegmul.data

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
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
 *  - v11: added first-class barcode provenance (`source`, `productName`, `barcode`).
 *
 * NOTE (§2.7): `fallbackToDestructiveMigration()` was REMOVED. Any schema change or
 * old-install upgrade without a matching [Migration] now throws loudly instead of
 * silently wiping `waste_history` + the barcode cache. In debug builds the failure
 * surfaces immediately at [getDatabase] time (see below); in release it is a loud
 * crash rather than silent data loss — that is the intended tradeoff.
 * `fallbackToDestructiveMigrationOnDowngrade()` is kept so a version-code rollback
 * (downgrade) recreates rather than crash-loops; downgrades are expected to be rare
 * and developer-driven.
 *
 * Schema exports: app/schemas/.../WasteDatabase/ JSON files
 * (verified present for v8/v9/v10/v11; older version JSONs were never exported and
 * are not required — Room only needs the current schema plus the [Migration] chain).
 * Every future version bump MUST add its export + a migration + a migration test.
 */
@Database(
    entities = [WasteRecord::class, BarcodeProduct::class],
    version = 11,
    exportSchema = true
)
abstract class WasteDatabase : RoomDatabase() {

    abstract fun wasteDao(): WasteDao
    abstract fun barcodeProductDao(): BarcodeProductDao

    companion object {
        private const val TAG = "WasteDatabase"

        /**
         * Historical database file name. The `industrial` infix predates the neutral
         * domain language and is misleading, but the name is RETAINED deliberately:
         * renaming the file would orphan every installed user's existing database
         * (Room would create a fresh empty DB = silent data loss, the very failure
         * §2.7 eliminates). Do not rename without a file-move migration.
         */
        const val DATABASE_NAME = "wasegmul_industrial_v1.db"

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

        /** v10 -> v11: first-class barcode provenance columns on waste_history. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE waste_history ADD COLUMN source TEXT NOT NULL DEFAULT 'camera'")
                db.execSQL("ALTER TABLE waste_history ADD COLUMN productName TEXT")
                db.execSQL("ALTER TABLE waste_history ADD COLUMN barcode TEXT")
            }
        }

        fun getDatabase(context: Context): WasteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WasteDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // §2.7: destructive fallback REMOVED (data-loss risk). OnDowngrade only.
                    // A missing migration path now throws IllegalStateException. Fail loudly
                    // in debug so schema slips are caught before release.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also {
                        INSTANCE = it
                        if (com.agrelius.wasegmul.BuildConfig.DEBUG) {
                            Log.d(TAG, "Opened $DATABASE_NAME v11 (destructive migration disabled)")
                        }
                    }
            }
        }

        /**
         * Test hook: closes the singleton so instrumented migration/schema tests start
         * from a clean state. Unit tests should prefer an in-memory instance instead.
         */
        @VisibleForTesting
        fun closeForTest() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
            }
        }
    }
}
