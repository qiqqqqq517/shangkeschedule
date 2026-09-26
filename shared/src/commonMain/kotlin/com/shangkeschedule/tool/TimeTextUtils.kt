package com.shangkeschedule.tool

/**
 * 时间文本解析工具。
 *
 * 存在意义：课表网格与作息解析运行在高频协程内（combine 数据流与 60 秒轮询），
 * 一旦解析抛出异常（如 NumberFormatException），协程会被取消，
 * 表现为「整张周课表停止刷新且无任何错误提示」。
 * 因此这里所有解析一律返回 null，绝不抛异常，由调用方决定跳过或降级。
 */
object TimeTextUtils {

    /**
     * 解析 "HH:mm" / "H:m" 形式的作息时间，返回当天 0 点起的分钟数。
     *
     * 仅接受「冒号分隔的两段纯数字」且落在 0..23 / 0..59 范围内；
     * 其余（空串、单段、三段、非数字、越界）一律返回 null。
     */
    fun parseMinutesOfDayOrNull(value: String?): Int? {
        if (value.isNullOrBlank()) return null

        val parts = value.trim().split(":")
        if (parts.size != 2) return null

        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return hour * 60 + minute
    }

    /** 判断给定文本是否为合法的 "HH:mm" / "H:m" 作息时间。 */
    fun isValidTimeOfDay(value: String?): Boolean = parseMinutesOfDayOrNull(value) != null
}
