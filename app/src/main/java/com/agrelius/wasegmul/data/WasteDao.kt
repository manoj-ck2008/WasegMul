package com.agrelius.wasegmul.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Query("SELECT * FROM waste_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<WasteRecord>>

    @Query("SELECT * FROM waste_history ORDER BY timestamp DESC LIMIT 5")
    fun getRecentHistory(): Flow<List<WasteRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: WasteRecord): Long

    @Query("UPDATE waste_history SET feedback = :feedback WHERE id = :recordId")
    suspend fun updateFeedback(recordId: Long, feedback: String)

    @Query("UPDATE waste_history SET correctedSubclass = :correction WHERE id = :recordId")
    suspend fun updateCorrection(recordId: Long, correction: String)

    @Query("DELETE FROM waste_history")
    suspend fun clearHistory()
}
