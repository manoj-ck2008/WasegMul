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

    /** Atomic correction: subclass + re-derived category + re-derived weight. */
    @Query("UPDATE waste_history SET correctedSubclass = :correction, category = :category, estimatedWeight = :weight WHERE id = :recordId")
    suspend fun updateCorrectionFull(recordId: Long, correction: String, category: String, weight: Double): Int

    @Query("SELECT COUNT(*) FROM waste_history")
    fun getRecordCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM waste_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecordsSince(since: Long): List<WasteRecord>

    @Query("SELECT * FROM waste_history WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryByCategory(category: String, limit: Int = 500): Flow<List<WasteRecord>>

    @Query("SELECT COUNT(*) FROM waste_history")
    suspend fun getCount(): Int

    /**
     * Bounded export page (§3.38). Callers MUST page with this (or [getExportPageAfterId])
     * instead of loading the full table: the unbounded [getAllForExport] is retained
     * only for small-table compat and is OOM-prone on large histories.
     */
    @Query("SELECT * FROM waste_history ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getExportPage(limit: Int, offset: Int): List<WasteRecord>

    /**
     * Keyset (cursor) export page — stable under concurrent inserts, unlike OFFSET.
     * Paging3-ready: backs a `PagingSource<Int, WasteRecord>` keyed on autoincrement id.
     */
    @Query("SELECT * FROM waste_history WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getExportPageAfterId(afterId: Long, limit: Int): List<WasteRecord>

    /**
     * Keyset history page for UI pagination (newest-first). Paging3-ready alongside
     * [getExportPageAfterId]; pass the last visible id as [beforeId], `Long.MAX_VALUE`
     * for the first page.
     */
    @Query("SELECT * FROM waste_history WHERE id < :beforeId ORDER BY id DESC LIMIT :limit")
    suspend fun getHistoryPageBeforeId(beforeId: Long, limit: Int): List<WasteRecord>

    /** Targeted count for workers/widgets — avoids loading entities (§3.38/§3.45). */
    @Query("SELECT COUNT(*) FROM waste_history WHERE timestamp >= :since")
    suspend fun getCountSince(since: Long): Int

    /** Targeted weight sum for workers — avoids loading entities (§3.38/§3.45). */
    @Query("SELECT COALESCE(SUM(estimatedWeight), 0.0) FROM waste_history WHERE timestamp >= :since")
    suspend fun getWeightSumSince(since: Long): Double

    /**
     * Unbounded full-table load. Retained for compat/tests only — prefer
     * [getExportPage]/[getExportPageAfterId] (OOM risk on large histories).
     */
    @Query("SELECT * FROM waste_history ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<WasteRecord>

    @Query("DELETE FROM waste_history")
    suspend fun clearHistory()

    @Query("DELETE FROM waste_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
