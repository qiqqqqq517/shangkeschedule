package com.shangkeschedule.data.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * 「月-日」校验口径下某月的最大天数（2 月按闰年处理，返回 29）。
 *
 * 用于不含年份的 `MM-dd` 字符串校验：放宽到闰年口径后 `02-29` 才合法。
 * 若需要某个**具体年份**的真实天数，请改用 kotlinx-datetime 的日期运算。
 */
internal fun daysInMonth(month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> 29
    else -> 0
}

/**
 * 按设定的每周起始日（1=周一 … 7=周日）向前对齐到 [date] 所在周的第一天。
 *
 * 越界的 [firstDayOfWeekInt] 收敛到 1..7，保证调用方不会因脏配置崩溃。
 */
internal fun startOfWeek(date: LocalDate, firstDayOfWeekInt: Int): LocalDate {
    val targetFirstDay = DayOfWeek(firstDayOfWeekInt.coerceIn(1, 7))
    var current = date
    while (current.dayOfWeek != targetFirstDay) {
        current = current.minus(1, DateTimeUnit.DAY)
    }
    return current
}
