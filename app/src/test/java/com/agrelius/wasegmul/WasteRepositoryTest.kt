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
        var failOnInsert = false
        private val flow = MutableStateFlow<List<EntityRecord>>(emptyList())

        override fun getAllHistory(limit: Int): Flow<List<EntityRecord>> = flow
        override fun getRecentHistory(limit: Int): Flow<List<EntityRecord>> = flow

        override suspend fun getRecordById(id: Long): EntityRecord? =
            records.find { it.id == id }

        override suspend fun insertRecord(record: EntityRecord): Long {
            if (failOnInsert) throw java.io.IOException("fake IO failure")
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

        override suspend fun updateCorrectionFull(recordId: Long, correction: String, category: String, weight: Double): Int {
            val idx = records.indexOfFirst { it.id == recordId }
            if (idx == -1) return 0
            records[idx] = records[idx].copy(correctedSubclass = correction, category = category, estimatedWeight = weight)
            flow.value = records.toList()
            return 1
        }

        override fun getRecordCountFlow(): Flow<Int> = kotlinx.coroutines.flow.flowOf(records.size)

        override suspend fun getRecordsSince(since: Long): List<EntityRecord> =
            records.filter { it.timestamp >= since }

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

        // Paged export/count/sum mirrors of the new DAO surface (data batch).
        override suspend fun getExportPage(limit: Int, offset: Int): List<EntityRecord> =
            records.sortedBy { it.id }.drop(offset).take(limit)

        override suspend fun getExportPageAfterId(afterId: Long, limit: Int): List<EntityRecord> =
            records.filter { it.id > afterId }.sortedBy { it.id }.take(limit)

        override suspend fun getHistoryPageBeforeId(beforeId: Long, limit: Int): List<EntityRecord> =
            records.filter { it.id < beforeId }.sortedByDescending { it.id }.take(limit)

        override suspend fun getCountSince(since: Long): Int =
            records.count { it.timestamp >= since }

        override suspend fun getWeightSumSince(since: Long): Double =
            records.filter { it.timestamp >= since }.sumOf { it.estimatedWeight }
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
    fun testUpdateCorrectionWithCategory_syncsWeight() = runBlocking {
        val fakeDao = FakeWasteDao()
        val repo = WasteRepository(fakeDao)

        // Simulate heavy appliance misclassified, then corrected to Paper (0.01kg).
        val record = WasteRecord(
            category = "E-Waste",
            subclass = "Air-Conditioner",
            confidence = 0.90f,
            estimatedWeight = 45.0
        )
        val id = repo.insert(record)

        val updated = repo.updateCorrectionWithCategory(id, "Paper")
        assertEquals(1, updated)

        val fetched = repo.getRecordById(id)
        assertNotNull(fetched)
        assertEquals("Paper", fetched?.correctedSubclass)
        assertEquals("Recyclable", fetched?.category)
        assertEquals(0.01, fetched?.estimatedWeight ?: -1.0, 0.0001)
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

    @Test
    fun insertCatching_successReturnsId() = runBlocking {
        val repo = WasteRepository(FakeWasteDao())

        val result = repo.insertCatching(
            WasteRecord(category = "Organic", subclass = "Banana Peel", confidence = 0.8f)
        )

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrThrow())
    }

    @Test
    fun insertCatching_dbFailureReturnsFailureNotThrow() = runBlocking {
        val fakeDao = FakeWasteDao().apply { failOnInsert = true }
        val repo = WasteRepository(fakeDao)

        val result = repo.insertCatching(
            WasteRecord(category = "Organic", subclass = "Banana Peel", confidence = 0.8f)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun exportAllPaged_pagesThroughKeysetInOrder() = runBlocking {
        val repo = WasteRepository(FakeWasteDao())
        repeat(5) { i ->
            repo.insert(WasteRecord(category = "Organic", subclass = "Item $i", confidence = 0.8f))
        }

        val result = repo.exportAllPaged(pageSize = 2)

        assertTrue(result.isSuccess)
        val exported = result.getOrThrow()
        assertEquals(5, exported.size)
        assertEquals((1L..5L).toList(), exported.map { it.id })
    }

    @Test
    fun pagination_rejectsBadArguments() = runBlocking {
        val repo = WasteRepository(FakeWasteDao())

        try {
            repo.getExportPage(limit = 0, offset = 0)
            fail("expected IllegalArgumentException for limit=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            repo.getHistoryPageBeforeId(beforeId = Long.MAX_VALUE, limit = 501)
            fail("expected IllegalArgumentException for limit=501")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun countAndWeightSince_matchInsertedRows() = runBlocking {
        val repo = WasteRepository(FakeWasteDao())
        val now = System.currentTimeMillis()
        repo.insert(
            WasteRecord(
                category = "Recyclable", subclass = "Plastic", confidence = 0.9f,
                estimatedWeight = 0.5, timestamp = now
            )
        )
        repo.insert(
            WasteRecord(
                category = "Organic", subclass = "Peel", confidence = 0.9f,
                estimatedWeight = 0.25, timestamp = now
            )
        )

        assertEquals(2, repo.getCountSince(now - 1000))
        assertEquals(0, repo.getCountSince(now + 60_000))
        assertEquals(0.75, repo.getWeightSumSince(now - 1000), 0.0001)
    }
}
