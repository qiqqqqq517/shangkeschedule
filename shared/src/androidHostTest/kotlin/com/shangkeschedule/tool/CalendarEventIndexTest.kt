package com.shangkeschedule.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventIndexTest {
    private fun row(id: Long, owned: Boolean) = CalendarEventRow(
        id, 100L, 200L, "课程", "教室", if (owned) CalendarOwnerMark.stamp("教师") else "手工事件", 10
    )

    @Test
    fun manualEventsAtSameTimeAreNeverDeleted() {
        val index = indexCalendarEvents(listOf(row(1, false), row(2, false)))
        assertTrue(index.duplicateIds.isEmpty())
        assertFalse(index.byStart.getValue(100L).ownedByUs)
    }

    @Test
    fun ownedCourseIsKeptWhenManualEventSharesStartInEitherOrder() {
        for (events in listOf(listOf(row(1, false), row(2, true)), listOf(row(2, true), row(1, false)))) {
            val index = indexCalendarEvents(events)
            assertEquals(2L, index.byStart.getValue(100L).eventId)
            assertTrue(index.duplicateIds.isEmpty())
        }
    }

    @Test
    fun duplicateOwnedCoursesOnlyDeletesExtraOwnedEntries() {
        val index = indexCalendarEvents(listOf(row(1, false), row(2, true), row(3, true)))
        assertEquals(2L, index.byStart.getValue(100L).eventId)
        assertEquals(listOf(3L), index.duplicateIds)
    }

    @Test
    fun unchangedStampedCourseMatchesReadBackFingerprint() {
        val description = CalendarOwnerMark.stamp("教师")
        val stored = indexCalendarEvents(listOf(row(2, true))).byStart.getValue(100L)
        assertEquals(stored.fingerprint, calendarEventFingerprint(200L, "课程", "教室", description, 10))
    }
}
