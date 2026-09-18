package com.agrelius.wasegmul.repository

import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.data.WasteDao
import com.agrelius.wasegmul.data.sanitizedForWrite
import com.agrelius.wasegmul.data.toCommon
import com.agrelius.wasegmul.data.toEntity
import com.agrelius.wasegmul.utils.SafeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for persisted classification records.
 * Converts between the Room entity and the shared domain model at the boundary.
 *
 * ### Error policy (§3 audit)
 * The legacy suspend members throw on DB failure (kept for existing callers).
 * New callers MUST prefer the `…Catching` / paged members, which return [Result]
 * (with [CancellationException] always rethrown, never captured) and log failures
 * via [SafeLog] so worker/export crashes stay observable.
 */
interface WasteRepository {
    val allHistory: Flow<List<WasteRecord>>
    val recentHistory: Flow<List<WasteRecord>>
    val totalCount: Flow<Int>

    fun getHistoryByCategory(category: String, limit: Int = 500): Flow<List<WasteRecord>>
    suspend fun getCount(): Int
    suspend fun getAllForExport(): List<WasteRecord>
    suspend fun getRecordsSince(since: Long): List<WasteRecord>

    suspend fun insert(record: WasteRecord): Long
    suspend fun getRecordById(id: Long): WasteRecord?
    suspend fun updateFeedback(recordId: Long, feedback: String): Int
    suspend fun updateCorrection(recordId: Long, correction: String): Int
    suspend fun deleteById(id: Long): Int
    suspend fun updateCorrectionWithCategory(recordId: Long, correction: String): Int
    suspend fun clear()

    // ### Paged / aggregate surface (§3.38) — prefer over full-table loads.
    /** Bounded OFFSET export page (oldest-first). */
    suspend fun getExportPage(limit: Int, offset: Int): List<WasteRecord>
    /** Keyset export page (stable under concurrent inserts); `afterId = 0` starts. */
    suspend fun getExportPageAfterId(afterId: Long, limit: Int): List<WasteRecord>
    /** Newest-first history page; `beforeId = Long.MAX_VALUE` starts. */
    suspend fun getHistoryPageBeforeId(beforeId: Long, limit: Int): List<WasteRecord>
    /** Targeted count for workers/widgets (no entity load). */
    suspend fun getCountSince(since: Long): Int
    /** Targeted weight sum for workers (no entity load). */
    suspend fun getWeightSumSince(since: Long): Double

    // ### Result surface — failures as values, never silent.
    /** Validated insert ([sanitizedForWrite]); failure is a logged [Result]. */
    suspend fun insertCatching(record: WasteRecord): Result<Long>
    /** Full export via keyset paging (OOM-safe); failure is a logged [Result]. */
    suspend fun exportAllPaged(pageSize: Int = 500): Result<List<WasteRecord>>

    companion object {
        operator fun invoke(wasteDao: WasteDao): WasteRepository = DefaultWasteRepository(wasteDao)
    }
}

class DefaultWasteRepository(private val wasteDao: WasteDao) : WasteRepository {

    private companion object {
        const val TAG = "WasteRepository"
    }

    override val allHistory: Flow<List<WasteRecord>> = wasteDao.getAllHistory().map { list ->
        list.map { it.toCommon() }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override val recentHistory: Flow<List<WasteRecord>> = wasteDao.getRecentHistory().map { list ->
        list.map { it.toCommon() }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override val totalCount: Flow<Int> = wasteDao.getRecordCountFlow()
        .distinctUntilChanged().flowOn(Dispatchers.Default)

    override fun getHistoryByCategory(category: String, limit: Int): Flow<List<WasteRecord>> =
        wasteDao.getHistoryByCategory(category, limit).map { list ->
            list.map { it.toCommon() }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun getCount(): Int = wasteDao.getCount()

    override suspend fun getAllForExport(): List<WasteRecord> =
        wasteDao.getAllForExport().map { it.toCommon() }

    override suspend fun getRecordsSince(since: Long): List<WasteRecord> =
        wasteDao.getRecordsSince(since).map { it.toCommon() }

    override suspend fun insert(record: WasteRecord): Long =
        wasteDao.insertRecord(record.toEntity())

    /** O(1) lookup of a single record. */
    override suspend fun getRecordById(id: Long): WasteRecord? =
        wasteDao.getRecordById(id)?.toCommon()

    override suspend fun updateFeedback(recordId: Long, feedback: String): Int =
        wasteDao.updateFeedback(recordId, feedback)

    override suspend fun updateCorrection(recordId: Long, correction: String): Int =
        wasteDao.updateCorrection(recordId, correction)

    /** Deletes a single record by ID. @return number of rows deleted. */
    override suspend fun deleteById(id: Long): Int =
        wasteDao.deleteById(id)

    /**
     * Updates the corrected subclass and re-derives the category to maintain
     * data integrity. When a user corrects "Battery" (E-Waste) to "Plastic"
     * (Recyclable), both fields must stay consistent.
     */
    override suspend fun updateCorrectionWithCategory(
        recordId: Long,
        correction: String
    ): Int {
        val correctedCategory = com.agrelius.wasegmul.WasteMapping.getCategory(correction)
        if (correctedCategory == com.agrelius.wasegmul.WasteMapping.UNKNOWN) {
            // Unmapped correction: store subclass only, do not corrupt category/weight.
            SafeLog.w(TAG, "updateCorrectionWithCategory: '$correction' unmapped; storing subclass only (id=$recordId)")
            return wasteDao.updateCorrection(recordId, correction)
        }
        // Atomic: keep category AND weight consistent with the corrected subclass so
        // eco-impact analytics are not corrupted (e.g. Paper at 45kg).
        val correctedWeight = com.agrelius.wasegmul.WasteMapping.getWeight(correction)
        return wasteDao.updateCorrectionFull(recordId, correction, correctedCategory, correctedWeight)
    }

    override suspend fun clear() {
        wasteDao.clearHistory()
    }

    override suspend fun getExportPage(limit: Int, offset: Int): List<WasteRecord> {
        require(limit in 1..2000) { "limit must be in 1..2000 (was $limit)" }
        require(offset >= 0) { "offset must be >= 0 (was $offset)" }
        return wasteDao.getExportPage(limit, offset).map { it.toCommon() }
    }

    override suspend fun getExportPageAfterId(afterId: Long, limit: Int): List<WasteRecord> {
        require(limit in 1..2000) { "limit must be in 1..2000 (was $limit)" }
        require(afterId >= 0) { "afterId must be >= 0 (was $afterId)" }
        return wasteDao.getExportPageAfterId(afterId, limit).map { it.toCommon() }
    }

    override suspend fun getHistoryPageBeforeId(beforeId: Long, limit: Int): List<WasteRecord> {
        require(limit in 1..500) { "limit must be in 1..500 (was $limit)" }
        return wasteDao.getHistoryPageBeforeId(beforeId, limit).map { it.toCommon() }
    }

    override suspend fun getCountSince(since: Long): Int = wasteDao.getCountSince(since)

    override suspend fun getWeightSumSince(since: Long): Double = wasteDao.getWeightSumSince(since)

    override suspend fun insertCatching(record: WasteRecord): Result<Long> {
        return try {
            Result.success(wasteDao.insertRecord(record.toEntity().sanitizedForWrite()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "insertCatching failed (category=${record.category})", e)
            Result.failure(e)
        }
    }

    override suspend fun exportAllPaged(pageSize: Int): Result<List<WasteRecord>> {
        require(pageSize in 1..2000) { "pageSize must be in 1..2000 (was $pageSize)" }
        return try {
            val out = mutableListOf<WasteRecord>()
            var afterId = 0L
            while (true) {
                val page = getExportPageAfterId(afterId, pageSize)
                if (page.isEmpty()) break
                out.addAll(page)
                afterId = page.last().id
                if (page.size < pageSize) break
            }
            Result.success(out)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "exportAllPaged failed", e)
            Result.failure(e)
        }
    }
}
