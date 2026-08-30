package com.shangkeschedule.data.db.main

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * Room 实体类，代表“作息方案元信息”数据表。
 *
 * 与 TimeSlot（实际的时间段数据）分离，用于存储同一课表下每套作息方案
 * （如夏令时 / 冬令时）的生效日期范围。生效日期范围使用“月-日”（MM-dd）表示，
 * 每年自动重复，符合冬令时/夏令时“每年固定日期切换”的语义。
 *
 * @param courseTableId 所属课表 ID。
 * @param schemeId 作息方案 ID，与 TimeSlot.schemeId 对应。
 * @param startMonthDay 生效开始日期（含），格式 "MM-dd"；为 null 表示该方案不参与自动切换。
 * @param endMonthDay 生效结束日期（含），格式 "MM-dd"；支持跨年（end < start 表示跨年区间）。
 */
@Entity(
    tableName = "time_slot_schemes",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["courseTableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseTableId"])],
    primaryKeys = ["courseTableId", "schemeId"]
)
data class TimeSlotScheme(
    val courseTableId: String,
    val schemeId: String = TimeSlot.DEFAULT_SCHEME_ID,
    val startMonthDay: String? = null,
    val endMonthDay: String? = null
) {
    /**
     * 判断给定“月-日”是否落在本方案的生效区间内。
     *
     * 若开始或结束日期解析失败（格式错误、或是不存在的日期如 9-31 / 2-30），
     * 视为该方案不参与自动切换，直接返回 false。
     *
     * @param monthDay 形如 monthNumber * 100 + dayOfMonth 的整数值（如 5月1日 = 501）。
     */
    fun isActiveAt(monthDay: Int): Boolean {
        val start = startMonthDay?.let(::parseMonthDay) ?: return false
        val end = endMonthDay?.let(::parseMonthDay) ?: return false
        if (start < 0 || end < 0) return false // 存在不合法日期时，该方案不参与自动切换
        return isWithinRange(monthDay, start, end)
    }

    companion object {
        /**
         * 将 "MM-dd" 字符串解析为 monthNumber * 100 + dayOfMonth 的整数值。
         *
         * 解析失败（格式错误）或日期不存在（如 9-31、2-30、13-01）时返回 -1。
         * 2 月按闰年处理，允许 02-29。
         */
        fun parseMonthDay(value: String): Int {
            val parts = value.split("-")
            val month = parts.getOrNull(0)?.toIntOrNull() ?: return -1
            val day = parts.getOrNull(1)?.toIntOrNull() ?: return -1
            if (month !in 1..12) return -1
            if (day !in 1..daysInMonth(month)) return -1 // 拦截 9-31 等不存在的日期
            return month * 100 + day
        }

        /** 返回某月的天数（2 月按闰年处理）。 */
        private fun daysInMonth(month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> 29
            else -> 0
        }

        /**
         * 判断 [value] 是否落在 [start]..[end] 区间内，支持跨年（start > end）。
         */
        fun isWithinRange(value: Int, start: Int, end: Int): Boolean {
            return if (start <= end) {
                value in start..end
            } else {
                value >= start || value <= end
            }
        }
    }
}
