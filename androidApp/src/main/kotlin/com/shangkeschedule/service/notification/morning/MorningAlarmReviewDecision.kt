package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.data.model.MorningAlarmWeeklyTimes
import com.shangkeschedule.notification.plan.MorningAlarmSemesterProjection
import com.shangkeschedule.notification.plan.MorningAlarmWeeklyRisk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

internal object MorningAlarmReviewDecision {
    enum class Notice { POST, CANCEL, KEEP }

    fun notice(enabled: Boolean, pending: Boolean, clearResolved: Boolean): Notice = when {
        !enabled -> Notice.CANCEL
        pending -> Notice.POST
        clearResolved -> Notice.CANCEL
        else -> Notice.KEEP
    }

    fun requiresReview(settings: AppSettingsModel, confirmed: Map<DayOfWeek, LocalTime>, projection: MorningAlarmSemesterProjection.Result): Boolean {
        if (settings.morningAlarmMode != MorningAlarmMode.HYBRID_WEEKLY) return settings.morningAlarmWeeklyTimes.isNotEmpty()
        val times = settings.morningAlarmWeeklyTimes
        if (times.isEmpty() || times != MorningAlarmWeeklyTimes.normalize(times)) return true
        val expected = times.mapKeys { (day, _) -> DayOfWeek.entries.first { it.isoDayNumber == day } }
            .mapValues { (_, value) -> LocalTime.parse(value) }
        if (confirmed != expected || !projection.complete) return true
        val start = projection.coverageStart ?: return true
        val end = projection.coverageEnd ?: return true
        return MorningAlarmWeeklyRisk.assess(start, end, true, projection.alarms, expected).unsafe.isNotEmpty()
    }

    fun sameSettings(first: AppSettingsModel, second: AppSettingsModel): Boolean =
        first.morningAlarmEnabled == second.morningAlarmEnabled &&
            first.morningAlarmMode == second.morningAlarmMode &&
            first.morningAlarmWeeklyTimes == second.morningAlarmWeeklyTimes &&
            first.morningAlarmWeeklyRevision == second.morningAlarmWeeklyRevision &&
            first.morningAlarmLeadMinutes == second.morningAlarmLeadMinutes &&
            first.currentCourseTableId == second.currentCourseTableId &&
            first.skippedDates == second.skippedDates
}
