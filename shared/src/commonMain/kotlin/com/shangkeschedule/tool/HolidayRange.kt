package com.shangkeschedule.tool

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 跳过日期（节假日 / 停课）的区间展开工具。
 *
 * 手动维护节假日时，用户给的是「起止日期」这样的一整段（寒假、国庆…），
 * 而 `AppSettingsModel.skippedDates` 存的是 `yyyy-MM-dd` 的日期集合，
 * 这里负责把前者展开成后者。
 *
 * 纯函数、无平台依赖，便于单测（见 `HolidayRangeTest`）。
 */
object HolidayRange {

    /**
     * 单次批量添加允许的最长天数（含首尾）。
     *
     * 上限不是功能限制而是误操作保护：日期选择器可以一路选到 2100 年，
     * 若不加限制，一次误选就会往设置里写入上万条日期，
     * 之后每一次设置变更都会带着它重排提醒与闹钟。
     */
    const val MAX_DAYS = 366

    /**
     * 展开 [start]..[end]（含首尾）为 `yyyy-MM-dd` 列表。
     *
     * @return 区间合法时返回日期列表；[end] 早于 [start]、或区间超过
     *         [MAX_DAYS] 天时返回 null，由调用方据此提示用户。
     */
    fun expand(start: LocalDate, end: LocalDate): List<String>? {
        if (end < start) return null
        val dates = ArrayList<String>()
        var cursor = start
        while (cursor <= end) {
            if (dates.size >= MAX_DAYS) return null
            dates += cursor.toString()
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return dates
    }
}
