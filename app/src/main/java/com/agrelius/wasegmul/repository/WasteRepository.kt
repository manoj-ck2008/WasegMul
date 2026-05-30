package com.agrelius.wasegmul.repository

import com.agrelius.wasegmul.data.WasteDao
import com.agrelius.wasegmul.data.WasteRecord
import kotlinx.coroutines.flow.Flow

class WasteRepository(private val wasteDao: WasteDao) {
    val allHistory: Flow<List<WasteRecord>> = wasteDao.getAllHistory()
    val recentHistory: Flow<List<WasteRecord>> = wasteDao.getRecentHistory()

    suspend fun insert(record: WasteRecord): Long {
        return wasteDao.insertRecord(record)
    }

    suspend fun updateFeedback(recordId: Long, feedback: String) {
        wasteDao.updateFeedback(recordId, feedback)
    }

    suspend fun updateCorrection(recordId: Long, correction: String) {
        wasteDao.updateCorrection(recordId, correction)
    }

    suspend fun clear() {
        wasteDao.clearHistory()
    }
}
