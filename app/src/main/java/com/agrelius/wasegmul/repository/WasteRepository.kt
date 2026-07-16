package com.agrelius.wasegmul.repository

import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.data.WasteDao
import com.agrelius.wasegmul.data.toCommon
import com.agrelius.wasegmul.data.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for persisted classification records.
 * Converts between the Room entity and the shared domain model at the boundary.
 */
class WasteRepository(private val wasteDao: WasteDao) {

    val allHistory: Flow<List<WasteRecord>> = wasteDao.getAllHistory().map { list ->
        list.map { it.toCommon() }
    }

    val recentHistory: Flow<List<WasteRecord>> = wasteDao.getRecentHistory().map { list ->
        list.map { it.toCommon() }
    }

    suspend fun insert(record: WasteRecord): Long =
        wasteDao.insertRecord(record.toEntity())

    /** O(1) lookup of a single record. */
    suspend fun getRecordById(id: Long): WasteRecord? =
        wasteDao.getRecordById(id)?.toCommon()

    suspend fun updateFeedback(recordId: Long, feedback: String): Int =
        wasteDao.updateFeedback(recordId, feedback)

    suspend fun updateCorrection(recordId: Long, correction: String): Int =
        wasteDao.updateCorrection(recordId, correction)

    suspend fun clear() {
        wasteDao.clearHistory()
    }
}
