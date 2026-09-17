package com.agrelius.wasegmul.repository

import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.data.WasteDao
import com.agrelius.wasegmul.data.toCommon
import com.agrelius.wasegmul.data.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for persisted classification records.
 * Converts between the Room entity and the shared domain model at the boundary.
 */
interface WasteRepository {
    val allHistory: Flow<List<WasteRecord>>
    val recentHistory: Flow<List<WasteRecord>>

    fun getHistoryByCategory(category: String, limit: Int = 500): Flow<List<WasteRecord>>
    suspend fun getCount(): Int
    suspend fun getAllForExport(): List<WasteRecord>

    suspend fun insert(record: WasteRecord): Long
    suspend fun getRecordById(id: Long): WasteRecord?
    suspend fun updateFeedback(recordId: Long, feedback: String): Int
    suspend fun updateCorrection(recordId: Long, correction: String): Int
    suspend fun deleteById(id: Long): Int
    suspend fun updateCorrectionWithCategory(recordId: Long, correction: String): Int
    suspend fun clear()

    companion object {
        operator fun invoke(wasteDao: WasteDao): WasteRepository = DefaultWasteRepository(wasteDao)
    }
}

class DefaultWasteRepository(private val wasteDao: WasteDao) : WasteRepository {

    override val allHistory: Flow<List<WasteRecord>> = wasteDao.getAllHistory().map { list ->
        list.map { it.toCommon() }
    }.flowOn(Dispatchers.Default)

    override val recentHistory: Flow<List<WasteRecord>> = wasteDao.getRecentHistory().map { list ->
        list.map { it.toCommon() }
    }.flowOn(Dispatchers.Default)

    override fun getHistoryByCategory(category: String, limit: Int): Flow<List<WasteRecord>> =
        wasteDao.getHistoryByCategory(category, limit).map { list ->
            list.map { it.toCommon() }
        }.flowOn(Dispatchers.Default)

    override suspend fun getCount(): Int = wasteDao.getCount()

    override suspend fun getAllForExport(): List<WasteRecord> =
        wasteDao.getAllForExport().map { it.toCommon() }

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
        val rows = wasteDao.updateCorrection(recordId, correction)
        if (rows > 0) {
            val correctedCategory = com.agrelius.wasegmul.WasteMapping.getCategory(correction)
            if (correctedCategory != com.agrelius.wasegmul.WasteMapping.UNKNOWN) {
                wasteDao.updateCategory(recordId, correctedCategory)
            }
        }
        return rows
    }

    override suspend fun clear() {
        wasteDao.clearHistory()
    }
}
