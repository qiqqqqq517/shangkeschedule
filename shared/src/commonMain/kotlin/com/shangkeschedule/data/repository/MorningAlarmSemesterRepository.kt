package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.notification.plan.MorningAlarmSemesterProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

/** 获取并复核课表快照，供预览和创建前共用。 */
@Single
class MorningAlarmSemesterRepository(
    private val settingsRepository: AppSettingsRepository,
    private val courseRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository
) {
    data class Snapshot(
        val tableId: String,
        val leadMinutes: Int,
        val skippedDates: Set<String>,
        val config: CourseTableConfig?,
        val courses: List<CourseWithWeeks>,
        val schemes: List<TimeSlotScheme>,
        val slots: Map<String, List<TimeSlot>>
    )

    suspend fun read(today: LocalDate, leadMinutes: Int): MorningAlarmSemesterProjection.Result = try {
        val first = snapshot()
        val second = snapshot()
        projectStable(first, second, today, leadMinutes)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        MorningAlarmSemesterProjection.Result.incomplete()
    }

    private suspend fun snapshot(): Snapshot {
        val settings = settingsRepository.getAppSettingsOnce()
        val id = settings.currentCourseTableId
        if (id.isBlank()) return Snapshot(id, settings.morningAlarmLeadMinutes, settings.skippedDates, null, emptyList(), emptyList(), emptyMap())
        val config = settingsRepository.getCourseConfigOnce(id)
        val courses = courseRepository.getCoursesWithWeeksByTableId(id).first()
        val schemes = timeSlotRepository.getSchemeMetasOnce(id)
        val ids = (timeSlotRepository.getSchemeIdsByCourseTableId(id).first() +
            schemes.map { it.schemeId } + listOfNotNull(config?.currentSchemeId)).toSet()
        val slots = ids.associateWith { timeSlotRepository.getTimeSlotsByCourseTableId(id, it).first() }
        return Snapshot(id, settings.morningAlarmLeadMinutes, settings.skippedDates, config, courses, schemes, slots)
    }

    companion object {
        fun projectStable(first: Snapshot, second: Snapshot, today: LocalDate, leadMinutes: Int): MorningAlarmSemesterProjection.Result {
            if (first != second || first.tableId.isBlank() || first.config?.courseTableId != first.tableId ||
                first.leadMinutes != leadMinutes) return MorningAlarmSemesterProjection.Result.incomplete()
            return MorningAlarmSemesterProjection.project(first.config, first.courses, first.schemes, first.slots,
                first.skippedDates, today, leadMinutes)
        }
    }
}
