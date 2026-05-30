package com.agrelius.wasegmul.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WasteRecord::class], version = 5, exportSchema = false)
abstract class WasteDatabase : RoomDatabase() {
    abstract fun wasteDao(): WasteDao

    companion object {
        @Volatile
        private var INSTANCE: WasteDatabase? = null

        fun getDatabase(context: Context): WasteDatabase {
            return INSTANCE ?: synchronized(this) {
                // Room strictly stores data in the system's private app data folder, 
                // which is persistent and protected from standard cache clearing.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WasteDatabase::class.java,
                    "wasegmul_industrial_v1.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
