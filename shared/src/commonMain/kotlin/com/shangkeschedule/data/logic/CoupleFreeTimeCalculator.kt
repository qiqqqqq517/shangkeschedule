package com.shangkeschedule.data.logic

/**
 * D2「找共同空闲」（v4.66.0）的纯逻辑：把双方课表在某一周里占用的时间区间合并，
 * 再求出双方都没课、且足够长的空档。
 *
 * 刻意做成不含 Room / Compose 依赖的纯函数（只吃 `Int` 分钟数），
 * 这样 Host 单测可以直接覆盖合并、裁剪、最小空档、跨天等边界，
 * 真机只需要验证「界面把数据喂对了」。
 */

/** 一段被占用的时间（分钟数，从 00:00 起算）。 */
data class MinuteRange(val startMinutes: Int, val endMinutes: Int)

/** 一段双方都没课的时间。 */
data class FreeTimeBlock(
    val day: Int,
    val startMinutes: Int,
    val endMinutes: Int
) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}

object CoupleFreeTimeCalculator {

    /**
     * 小于这个时长的空档不算「共同空闲」。
     * 课间 10 分钟这种碎片对人没有意义，列出来只会淹没真正可约的时间段。
     */
    const val DEFAULT_MIN_DURATION_MINUTES = 30

    /**
     * 解析 "HH:MM" 为分钟数；非法输入返回 null（调用方自行跳过。
     * 自定义时间课程允许 "8:05" 这种不补零写法）。
     */
    fun parseToMinutes(hhmm: String?): Int? {
        val text = hhmm?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /**
     * 合并重叠或首尾相接的区间。
     * 首尾相接（下一节的开始 = 上一节的结束）也算连续占用：中间没有可用的空档。
     */
    fun mergeRanges(ranges: List<MinuteRange>): List<MinuteRange> {
        val sorted = ranges.filter { it.endMinutes > it.startMinutes }.sortedBy { it.startMinutes }
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<MinuteRange>()
        var currentStart = sorted.first().startMinutes
        var currentEnd = sorted.first().endMinutes
        for (range in sorted.drop(1)) {
            if (range.startMinutes <= currentEnd) {
                if (range.endMinutes > currentEnd) currentEnd = range.endMinutes
            } else {
                merged += MinuteRange(currentStart, currentEnd)
                currentStart = range.startMinutes
                currentEnd = range.endMinutes
            }
        }
        merged += MinuteRange(currentStart, currentEnd)
        return merged
    }

    /**
     * 求某一天在 [windowStartMinutes, windowEndMinutes] 之内的空闲区间。
     *
     * 占用区间会先被裁剪到窗口内（作息只覆盖第 1..N 节，窗口外的课程不该把空档吃掉）。
     */
    fun freeBlocksForDay(
        day: Int,
        busyRanges: List<MinuteRange>,
        windowStartMinutes: Int,
        windowEndMinutes: Int,
        minDurationMinutes: Int = DEFAULT_MIN_DURATION_MINUTES
    ): List<FreeTimeBlock> {
        if (windowEndMinutes <= windowStartMinutes) return emptyList()
        val clipped = busyRanges
            .map {
                MinuteRange(
                    startMinutes = maxOf(it.startMinutes, windowStartMinutes),
                    endMinutes = minOf(it.endMinutes, windowEndMinutes)
                )
            }
            .filter { it.endMinutes > it.startMinutes }
        val merged = mergeRanges(clipped)
        val blocks = mutableListOf<FreeTimeBlock>()
        var cursor = windowStartMinutes
        for (range in merged) {
            if (range.startMinutes - cursor >= minDurationMinutes) {
                blocks += FreeTimeBlock(day, cursor, range.startMinutes)
            }
            if (range.endMinutes > cursor) cursor = range.endMinutes
        }
        if (windowEndMinutes - cursor >= minDurationMinutes) {
            blocks += FreeTimeBlock(day, cursor, windowEndMinutes)
        }
        return blocks
    }

    /**
     * 求一组星期的共同空闲。
     *
     * @param days 需要计算的星期（1=周一…7=周日），顺序由调用方决定（界面按 1..7 传）。
     * @param busyByDay 键为星期，值为**双方合并后**的占用区间。
     */
    fun compute(
        days: List<Int>,
        busyByDay: Map<Int, List<MinuteRange>>,
        windowStartMinutes: Int,
        windowEndMinutes: Int,
        minDurationMinutes: Int = DEFAULT_MIN_DURATION_MINUTES
    ): List<FreeTimeBlock> {
        return days.flatMap { day ->
            freeBlocksForDay(
                day = day,
                busyRanges = busyByDay[day].orEmpty(),
                windowStartMinutes = windowStartMinutes,
                windowEndMinutes = windowEndMinutes,
                minDurationMinutes = minDurationMinutes
            )
        }.sortedWith(compareBy({ it.day }, { it.startMinutes }))
    }

    /** 把分钟数格式化成 "HH:MM"（用于结果文案与复制文本）。 */
    fun formatMinutes(minutes: Int): String {
        val hour = (minutes / 60).coerceIn(0, 23)
        val minute = (minutes % 60).coerceIn(0, 59)
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
}
