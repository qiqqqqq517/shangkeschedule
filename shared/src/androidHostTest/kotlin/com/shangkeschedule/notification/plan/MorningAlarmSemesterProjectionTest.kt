package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.repository.MorningAlarmSemesterRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorningAlarmSemesterProjectionTest {
    private val config = CourseTableConfig("table", semesterStartDate = "2026-09-07", semesterTotalWeeks = 3)
    private fun course(day: Int, weeks: List<Int>, start: String = "08:00", end: String = "08:45") =
        CourseWithWeeks(Course("a$day", "table", "课程", "", "", day, null, null, true, start, end, 0),
            weeks.map { CourseWeek("a$day", it) })

    private fun project(courses: List<CourseWithWeeks>, settings: CourseTableConfig = config,
                        slots: Map<String, List<TimeSlot>> = emptyMap(), schemes: List<TimeSlotScheme> = emptyList(),
                        skipped: Set<String> = emptySet()) = MorningAlarmSemesterProjection.project(
        settings, courses, schemes, slots, skipped, LocalDate(2026, 9, 7), 45)

    @Test fun expandsAlternatingWeeksAndSkippedDatesAcrossTheSemester() {
        val result = project(listOf(course(1, listOf(1, 2, 3)), course(2, listOf(1, 3))), skipped = setOf("2026-09-14"))
        assertTrue(result.complete)
        assertEquals(LocalDate(2026, 9, 7), result.coverageStart)
        assertEquals(LocalDate(2026, 9, 26), result.coverageEnd)
        assertEquals(listOf("2026-09-07", "2026-09-08", "2026-09-21", "2026-09-22"), result.alarms.map { it.courseDate.toString() })
    }

    @Test fun resolvesTimeSchemeForEachCourseDate() {
        val ordinary = course(1, listOf(1, 2, 3)).let { it.copy(course = it.course.copy(isCustomTime = false, startSection = 1, endSection = 1)) }
        val slots = mapOf("default" to listOf(TimeSlot(1, "08:00", "08:45", "table")),
            "winter" to listOf(TimeSlot(1, "09:00", "09:45", "table", schemeId = "winter")))
        val result = project(listOf(ordinary), config.copy(autoSwitchScheme = true), slots,
            listOf(TimeSlotScheme("table", "winter", "09-14", "12-31")))
        assertTrue(result.complete)
        assertEquals(listOf(LocalTime(7, 15), LocalTime(8, 15), LocalTime(8, 15)), result.alarms.map { it.alarmTime })
    }

    @Test fun invalidCourseTimeCannotProveThatDayHasNoCourse() {
        assertFalse(project(listOf(course(1, listOf(1), start = "bad"))).complete)
    }

    @Test fun nextDayCourseCanProduceAlarmOnPreviousWeekday() {
        val result = project(listOf(course(2, listOf(1), start = "00:20", end = "01:00")))
        assertTrue(result.complete)
        assertEquals(LocalDate(2026, 9, 7), result.alarms.single().alarmDate)
        assertEquals(LocalTime(23, 35), result.alarms.single().alarmTime)
    }

    @Test fun missingSemesterConfigurationIsIncomplete() {
        assertFalse(project(emptyList(), config.copy(semesterStartDate = null)).complete)
    }

    @Test fun beforeAndAfterSemesterCannotProveWeeklySafety() {
        val before = MorningAlarmSemesterProjection.project(config, emptyList(), emptyList(), emptyMap(), emptySet(), LocalDate(2026, 9, 6), 45)
        val after = MorningAlarmSemesterProjection.project(config, emptyList(), emptyList(), emptyMap(), emptySet(), LocalDate(2026, 9, 28), 45)
        assertFalse(before.complete)
        assertFalse(after.complete)
    }

    @Test fun aMidweekSemesterStartDoesNotPermitTheAlignedWeekBeforeStart() {
        val wednesdayStart = config.copy(semesterStartDate = "2026-09-09")
        val before = MorningAlarmSemesterProjection.project(wednesdayStart, emptyList(), emptyList(), emptyMap(), emptySet(), LocalDate(2026, 9, 8), 45)
        assertFalse(before.complete)
        val fromStart = MorningAlarmSemesterProjection.project(wednesdayStart, listOf(course(3, listOf(1))), emptyList(), emptyMap(), emptySet(), LocalDate(2026, 9, 9), 45)
        assertTrue(fromStart.complete)
        assertEquals(LocalDate(2026, 9, 9), fromStart.alarms.single().courseDate)
    }

    @Test fun changedSnapshotOrLeadCannotBeUsedToConfirmWeeklyAlarm() {
        val snapshot = MorningAlarmSemesterRepository.Snapshot("table", 45, emptySet(), config,
            listOf(course(1, listOf(1, 2, 3))), emptyList(), emptyMap())
        val date = LocalDate(2026, 9, 7)
        assertTrue(MorningAlarmSemesterRepository.projectStable(snapshot, snapshot, date, 45).complete)
        assertFalse(MorningAlarmSemesterRepository.projectStable(snapshot, snapshot.copy(skippedDates = setOf("2026-09-14")), date, 45).complete)
        assertFalse(MorningAlarmSemesterRepository.projectStable(snapshot, snapshot, date, 30).complete)
    }
}
