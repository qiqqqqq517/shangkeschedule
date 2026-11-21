package com.shangkeschedule.data.model

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import kotlinx.datetime.LocalDate

/** 完整作息快照；未来课程、导出和系统日历均按课程实际日期选择方案。 */
data class TimeSlotSchedule(
    val config: CourseTableConfig?,
    val schemes: List<TimeSlotScheme>,
    val slots: List<TimeSlot>
) {
    fun forDate(date: LocalDate): List<TimeSlot> = slots.filter {
        it.schemeId == resolveSchemeId(config, schemes, date)
    }

    companion object {
        fun resolveSchemeId(config: CourseTableConfig?, schemes: List<TimeSlotScheme>, date: LocalDate): String {
            val fallback = config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
            if (config?.autoSwitchScheme != true) return fallback
            val monthDay = date.monthNumber * 100 + date.dayOfMonth
            return schemes.firstOrNull { it.isActiveAt(monthDay) }?.schemeId ?: fallback
        }
    }
}
