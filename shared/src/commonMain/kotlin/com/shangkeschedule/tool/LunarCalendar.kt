package com.shangkeschedule.tool

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * 农历日期值对象。
 *
 * @param year 农历年（干支年对应的公历年）。
 * @param month 农历月，1..12。
 * @param day 农历日，1..30。
 * @param isLeap 是否为闰月。
 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeap: Boolean
) {
    /** 月名，如「正月」「七月」「腊月」；闰月前缀「闰」。 */
    val monthName: String
        get() = (if (isLeap) "闰" else "") + LUNAR_MONTH_NAMES[month - 1]

    /** 日名，如「初一」「廿八」「三十」。 */
    val dayName: String
        get() = LUNAR_DAY_NAMES[day - 1]

    /** 「七月廿八」形式的文本。 */
    val text: String
        get() = monthName + dayName
}

private val LUNAR_MONTH_NAMES = arrayOf(
    "正月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "冬月", "腊月"
)

private val LUNAR_DAY_NAMES = arrayOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
)

/**
 * 农历 / 节气 / 节日换算工具。
 *
 * 采用 1900–2100 年的农历数据表（每年 1 个 Int 编码 12/13 个月大小月与闰月），
 * 节气用「回归年 + 节气偏移分钟」经典算法近似（精度到日，够日历标签使用）。
 *
 * 用途：「日程」页周选择条的农历标签与当日副标题（农历七月廿八）。
 */
object LunarCalendar {

    private const val BASE_YEAR = 1900
    private const val MAX_YEAR = 2100

    /** 农历基准日：1900-01-31 为农历 1900 年正月初一。 */
    private val BASE_DATE = LocalDate(1900, 1, 31)

    /** 每年编码：低 4 位闰月月份，0x10000 位闰月大小，0x8000..0x10 位 12 个月大小。 */
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520 // 2100
    )

    private val SOLAR_TERM_NAMES = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    /** 24 节气相对基准日的分钟偏移。 */
    private val SOLAR_TERM_INFO = intArrayOf(
        0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
    )

    /** 节气基准时刻：1900-01-06 02:05 UTC 的毫秒数。 */
    private const val SOLAR_TERM_BASE_MS = -2208549300000L

    /** 回归年长度（毫秒）。 */
    private const val TROPICAL_YEAR_MS = 31556925974.7

    /** 公历节日（月 * 100 + 日）。 */
    private val SOLAR_FESTIVALS = mapOf(
        101 to "元旦", 214 to "情人节", 308 to "妇女节", 312 to "植树节",
        401 to "愚人节", 501 to "劳动节", 504 to "青年节", 601 to "儿童节",
        701 to "建党节", 801 to "建军节", 910 to "教师节", 1001 to "国庆节",
        1225 to "圣诞节"
    )

    /** 农历节日（农历月 * 100 + 农历日，闰月不计）。 */
    private val LUNAR_FESTIVALS = mapOf(
        101 to "春节", 115 to "元宵节", 202 to "龙抬头", 505 to "端午节",
        707 to "七夕节", 715 to "中元节", 815 to "中秋节", 909 to "重阳节",
        1208 to "腊八节", 1223 to "小年"
    )

    /**
     * 公历日期 → 农历日期。
     *
     * 超出 1900–2100 支持范围时夹取到边界年份，保证不抛异常。
     */
    fun toLunar(date: LocalDate): LunarDate {
        var offset = (date.toEpochDays() - BASE_DATE.toEpochDays()).toInt()
        var temp = 0
        var i = BASE_YEAR
        while (i <= MAX_YEAR && offset > 0) {
            temp = yearDays(i)
            offset -= temp
            i++
        }
        if (offset < 0) {
            offset += temp
            i--
        }
        val year = i.coerceIn(BASE_YEAR, MAX_YEAR)
        val leap = leapMonth(year)
        var isLeap = false
        i = 1
        while (i < 13 && offset > 0) {
            if (leap > 0 && i == leap + 1 && !isLeap) {
                i--
                isLeap = true
                temp = leapDays(year)
            } else {
                temp = monthDays(year, i)
            }
            if (isLeap && i == leap + 1) isLeap = false
            offset -= temp
            i++
        }
        if (offset == 0 && leap > 0 && i == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                i--
            }
        }
        if (offset < 0) {
            offset += temp
            i--
        }
        return LunarDate(
            year = year,
            month = i.coerceIn(1, 12),
            day = (offset + 1).coerceIn(1, 30),
            isLeap = isLeap
        )
    }

    /** 指定公历日期的节气名，非节气日返回 null。 */
    fun solarTermName(date: LocalDate): String? {
        val first = (date.month.number - 1) * 2
        if (solarTermDay(date.year, first) == date.day) return SOLAR_TERM_NAMES[first]
        if (solarTermDay(date.year, first + 1) == date.day) return SOLAR_TERM_NAMES[first + 1]
        return null
    }

    /** 指定公历日期的公历节日名，无则返回 null。 */
    fun solarFestival(date: LocalDate): String? =
        SOLAR_FESTIVALS[date.month.number * 100 + date.day]

    /** 指定公历日期对应的农历节日名（含除夕），无则返回 null。 */
    fun lunarFestival(date: LocalDate): String? {
        val lunar = toLunar(date)
        if (lunar.isLeap) return null
        LUNAR_FESTIVALS[lunar.month * 100 + lunar.day]?.let { return it }
        if (lunar.month == 12 && lunar.day == monthDays(lunar.year, 12)) return "除夕"
        return null
    }

    /**
     * 日历格子下方的一行标签。
     *
     * 优先级：节气 > 农历节日 > 公历节日 > 农历初一（显示月名）> 农历日名。
     */
    fun dayLabel(date: LocalDate): String {
        solarTermName(date)?.let { return it }
        lunarFestival(date)?.let { return it }
        solarFestival(date)?.let { return it }
        val lunar = toLunar(date)
        return if (lunar.day == 1) lunar.monthName else lunar.dayName
    }

    /** 「七月廿八」形式的农历文本（不含「农历」前缀）。 */
    fun lunarText(date: LocalDate): String = toLunar(date).text

    /** 某农历年的总天数（含闰月）。 */
    private fun yearDays(year: Int): Int {
        var sum = 348
        var bit = 0x8000
        while (bit > 0x8) {
            if ((LUNAR_INFO[year - BASE_YEAR] and bit) != 0) sum++
            bit = bit shr 1
        }
        return sum + leapDays(year)
    }

    /** 某农历年的闰月月份，无闰月返回 0。 */
    private fun leapMonth(year: Int): Int = LUNAR_INFO[year - BASE_YEAR] and 0xf

    /** 某农历年闰月的天数，无闰月返回 0。 */
    private fun leapDays(year: Int): Int =
        if (leapMonth(year) != 0) {
            if ((LUNAR_INFO[year - BASE_YEAR] and 0x10000) != 0) 30 else 29
        } else {
            0
        }

    /** 某农历年第 [month] 个（非闰）月的天数。 */
    private fun monthDays(year: Int, month: Int): Int =
        if ((LUNAR_INFO[year - BASE_YEAR] and (0x10000 shr month)) != 0) 30 else 29

    /** 第 [index] 个节气（0 = 小寒）在 [year] 年所属公历月中的日号。 */
    private fun solarTermDay(year: Int, index: Int): Int {
        val ms = SOLAR_TERM_BASE_MS +
            (TROPICAL_YEAR_MS * (year - BASE_YEAR)).toLong() +
            SOLAR_TERM_INFO[index].toLong() * 60_000L
        return Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).day
    }
}
