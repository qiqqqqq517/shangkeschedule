package com.shangkeschedule.data.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 早八闹钟按课表逐日计算，或结合用户确认的系统每周闹钟。 */
enum class MorningAlarmMode {
    SMART_DAILY,
    HYBRID_WEEKLY;

    companion object {
        fun parseOrNull(value: String?): MorningAlarmMode? = entries.find { it.name == value }
        fun fromString(value: String?): MorningAlarmMode = parseOrNull(value) ?: SMART_DAILY
    }
}

/** 周计划使用实际响铃日的 ISO 星期（周一为 1），时间严格为 24 小时制 HH:mm。 */
object MorningAlarmWeeklyTimes {
    fun normalize(times: Map<Int, String>): Map<Int, String> = times.entries
        .filter { (day, time) -> day in 1..7 && validTime(time) }
        .sortedBy { it.key }
        .associate { it.key to it.value }

    fun encode(times: Map<Int, String>): Set<String> =
        normalize(times).map { (day, time) -> "$day=$time" }.toSet()

    fun decode(values: Set<String>): Map<Int, String> {
        val entries = values.mapNotNull { raw ->
            val match = Regex("^([1-7])=([0-9]{2}:[0-9]{2})$").matchEntire(raw) ?: return@mapNotNull null
            val day = match.groupValues[1].toInt()
            val time = match.groupValues[2]
            if (validTime(time)) day to time else null
        }
        // 同一天被不同时间重复编码时无法判断用户意图，不使用该天。
        return entries.groupBy({ it.first }, { it.second }).mapNotNull { (day, times) ->
            times.distinct().singleOrNull()?.let { day to it }
        }.sortedBy { it.first }.toMap()
    }

    private fun validTime(time: String): Boolean =
        time.length == 5 && time[2] == ':' &&
            time[0] in '0'..'9' && time[1] in '0'..'9' &&
            time[3] in '0'..'9' && time[4] in '0'..'9' &&
            time.substring(0, 2).toInt() in 0..23 && time.substring(3, 5).toInt() in 0..59
}

/** 仅用于本机确认匹配；不写入上课备份。 */
@OptIn(ExperimentalUuidApi::class)
internal fun newMorningAlarmWeeklyRevision(): String = Uuid.random().toString()
