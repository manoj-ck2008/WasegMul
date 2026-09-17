package com.agrelius.wasegmul.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {

    @Query("SELECT * FROM waste_history ORDER BY timestamp DESC LIMIT :limit")
    fun getAllHistory(limit: Int = 500): Flow<List<WasteRecord>>

    @Query("SELECT * FROM waste_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 5): Flow<List<WasteRecord>>

    /** O(1) lookup of a single record by its primary key. */
    @Query("SELECT * FROM waste_history WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): WasteRecord?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: WasteRecord): Long

    /** @return number of rows updated (0 when [recordId] does not exist). */
    @Query("UPDATE waste_history SET feedback = :feedback WHERE id = :recordId")
    suspend fun updateFeedback(recordId: Long, feedback: String): Int

    /** @return number of rows updated (0 when [recordId] does not exist). */
    @Query("UPDATE waste_history SET correctedSubclass = :correction WHERE id = :recordId")
    suspend fun updateCorrection(recordId: Long, correction: String): Int

    /** @return number of rows updated (0 when [recordId] does not exist). */
    @Query("UPDATE waste_history SET category = :category WHERE id = :recordId")
    suspend fun updateCategory(recordId: Long, category: String): Int

    @Query("SELECT * FROM waste_history WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryByCategory(category: String, limit: Int = 500): Flow<List<WasteRecord>>

    @Query("SELECT COUNT(*) FROM waste_history")
    suspend fun getCount(): Int

    @Query("SELECT * FROM waste_history ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<WasteRecord>

    @Query("DELETE FROM waste_history")
    suspend fun clearHistory()

    @Query("DELETE FROM waste_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
