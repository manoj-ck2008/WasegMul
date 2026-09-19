package com.agrelius.wasegmul.ui.history

import com.agrelius.wasegmul.WasteRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HistoryGroupingTest {

    private fun createRecord(id: Long, timestamp: Long): WasteRecord {
        return WasteRecord(
            id = id,
            category = "Recyclable",
            subclass = "plastic_bottle",
            confidence = 0.95f,
            timestamp = timestamp,
            estimatedWeight = 0.05
        )
    }

    @Test
    fun testEmptyRecordsReturnsEmptyList() {
        val result = groupRecordsByTime(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGroupRecordsByTimeBuckets() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val now = cal.timeInMillis
        val todayRecord = createRecord(1L, now)

        val yesterday = now - (24L * 60 * 60 * 1000)
        val yesterdayRecord = createRecord(2L, yesterday)

        val thisWeek = now - (3L * 24 * 60 * 60 * 1000)
        val thisWeekRecord = createRecord(3L, thisWeek)

        val thisMonth = now - (15L * 24 * 60 * 60 * 1000)
        val thisMonthRecord = createRecord(4L, thisMonth)

        val older = now - (60L * 24 * 60 * 60 * 1000)
        val olderRecord = createRecord(5L, older)

        val records = listOf(todayRecord, yesterdayRecord, thisWeekRecord, thisMonthRecord, olderRecord)
        val grouped = groupRecordsByTime(records)

        val sections = grouped.map { it.section }
        assertTrue(sections.contains(HistoryTimeSection.TODAY))
        assertTrue(sections.contains(HistoryTimeSection.YESTERDAY))
        assertTrue(sections.contains(HistoryTimeSection.THIS_WEEK))
        assertTrue(sections.contains(HistoryTimeSection.THIS_MONTH))
        assertTrue(sections.contains(HistoryTimeSection.OLDER))

        val todayGroup = grouped.first { it.section == HistoryTimeSection.TODAY }
        assertEquals(1, todayGroup.records.size)
        assertEquals(1L, todayGroup.records[0].id)

        val olderGroup = grouped.first { it.section == HistoryTimeSection.OLDER }
        assertEquals(1, olderGroup.records.size)
        assertEquals(5L, olderGroup.records[0].id)
    }

    @Test
    fun testOnlyPopulatedSectionsReturned() {
        val now = System.currentTimeMillis()
        val records = listOf(
            createRecord(1L, now),
            createRecord(2L, now - 1000)
        )
        val grouped = groupRecordsByTime(records, now = now)
        assertEquals(1, grouped.size)
        assertEquals(HistoryTimeSection.TODAY, grouped[0].section)
        assertEquals(2, grouped[0].records.size)
    }

    @Test
    fun testExactMidnightBoundaryGrouping() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val recordJustBeforeMidnight = createRecord(10L, startOfToday - 1)
        val recordAtMidnight = createRecord(11L, startOfToday)

        val grouped = groupRecordsByTime(listOf(recordAtMidnight, recordJustBeforeMidnight), now = startOfToday + 3600000L)
        val todayGroup = grouped.find { it.section == HistoryTimeSection.TODAY }
        val yesterdayGroup = grouped.find { it.section == HistoryTimeSection.YESTERDAY }

        assertTrue(todayGroup != null)
        assertEquals(1, todayGroup?.records?.size)
        assertEquals(11L, todayGroup?.records?.get(0)?.id)

        assertTrue(yesterdayGroup != null)
        assertEquals(1, yesterdayGroup?.records?.size)
        assertEquals(10L, yesterdayGroup?.records?.get(0)?.id)
    }
}
