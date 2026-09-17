package com.agrelius.wasegmul

import com.agrelius.wasegmul.data.WasteDao
import com.agrelius.wasegmul.data.WasteRecord as EntityRecord
import com.agrelius.wasegmul.repository.WasteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WasteRepositoryTest {

    private class FakeWasteDao : WasteDao {
        val records = mutableListOf<EntityRecord>()
        private val flow = MutableStateFlow<List<EntityRecord>>(emptyList())

        override fun getAllHistory(limit: Int): Flow<List<EntityRecord>> = flow
        override fun getRecentHistory(limit: Int): Flow<List<EntityRecord>> = flow

        override suspend fun getRecordById(id: Long): EntityRecord? =
            records.find { it.id == id }

        override suspend fun insertRecord(record: EntityRecord): Long {
            val id = (records.maxOfOrNull { it.id } ?: 0L) + 1L
            val copy = record.copy(id = id)
            records.add(copy)
            flow.value = records.toList()
            return id
        }

        override suspend fun updateFeedback(recordId: Long, feedback: String): Int {
            val idx = records.indexOfFirst { it.id == recordId }
            if (idx == -1) return 0
            records[idx] = records[idx].copy(feedback = feedback)
            flow.value = records.toList()
            return 1
        }

        override suspend fun updateCorrection(recordId: Long, correction: String): Int {
            val idx = records.indexOfFirst { it.id == recordId }
            if (idx == -1) return 0
            records[idx] = records[idx].copy(correctedSubclass = correction)
            flow.value = records.toList()
            return 1
        }

        override suspend fun updateCategory(recordId: Long, category: String): Int {
            val idx = records.indexOfFirst { it.id == recordId }
            if (idx == -1) return 0
            records[idx] = records[idx].copy(category = category)
            flow.value = records.toList()
            return 1
        }

        override fun getHistoryByCategory(category: String, limit: Int): Flow<List<EntityRecord>> =
            kotlinx.coroutines.flow.flowOf(records.filter { it.category == category }.take(limit))

        override suspend fun getCount(): Int = records.size

        override suspend fun getAllForExport(): List<EntityRecord> = records.sortedBy { it.timestamp }

        override suspend fun clearHistory() {
            records.clear()
            flow.value = emptyList()
        }

        override suspend fun deleteById(id: Long): Int {
            val removed = records.removeIf { it.id == id }
            if (removed) flow.value = records.toList()
            return if (removed) 1 else 0
        }
    }

    @Test
    fun testInsertAndGetRecord() = runBlocking {
        val fakeDao = FakeWasteDao()
        val repo = WasteRepository(fakeDao)

        val record = WasteRecord(
            category = "Recyclable",
            subclass = "Plastic",
            confidence = 0.95f
        )
        val id = repo.insert(record)
        val fetched = repo.getRecordById(id)

        assertNotNull(fetched)
        assertEquals("Recyclable", fetched?.category)
        assertEquals("Plastic", fetched?.subclass)
        assertEquals(0.95f, fetched?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun testUpdateCorrectionWithCategory() = runBlocking {
        val fakeDao = FakeWasteDao()
        val repo = WasteRepository(fakeDao)

        val record = WasteRecord(
            category = "Trash",
            subclass = "Miscellaneous Trash",
            confidence = 0.60f
        )
        val id = repo.insert(record)

        // Correct to "Battery", which maps to "E-Waste"
        val updated = repo.updateCorrectionWithCategory(id, "Battery")
        assertEquals(1, updated)

        val fetched = repo.getRecordById(id)
        assertNotNull(fetched)
        assertEquals("Battery", fetched?.correctedSubclass)
        assertEquals("E-Waste", fetched?.category)
    }

    @Test
    fun testDeleteById() = runBlocking {
        val fakeDao = FakeWasteDao()
        val repo = WasteRepository(fakeDao)

        val record = WasteRecord(
            category = "Organic",
            subclass = "Banana Peel",
            confidence = 0.88f
        )
        val id = repo.insert(record)
        assertEquals(1, fakeDao.records.size)

        val rowsDeleted = repo.deleteById(id)
        assertEquals(1, rowsDeleted)
        assertEquals(0, fakeDao.records.size)
        assertNull(repo.getRecordById(id))
    }

    @Test
    fun testCountAndExport() = runBlocking {
        val fakeDao = FakeWasteDao()
        val repo = WasteRepository(fakeDao)

        assertEquals(0, repo.getCount())

        repo.insert(WasteRecord(category = "Organic", subclass = "Organic", confidence = 0.9f))
        repo.insert(WasteRecord(category = "Recyclable", subclass = "Plastic", confidence = 0.85f))

        assertEquals(2, repo.getCount())
        val exportList = repo.getAllForExport()
        assertEquals(2, exportList.size)
    }
}
