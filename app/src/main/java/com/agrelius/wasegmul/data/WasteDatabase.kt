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
 *
 * NOTE: `fallbackToDestructiveMigration()` is kept ONLY as a last-resort safety net so a
 * future schema change never crashes the app on launch; every intentional schema change
 * MUST ship with an explicit [Migration] entry below.
 */
@Database(entities = [WasteRecord::class], version = 8, exportSchema = false)
abstract class WasteDatabase : RoomDatabase() {

    abstract fun wasteDao(): WasteDao

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

        fun getDatabase(context: Context): WasteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WasteDatabase::class.java,
                    "wasegmul_industrial_v1.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
