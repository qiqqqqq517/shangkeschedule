package com.shangkeschedule.widget

/**
 * 小组件课程筛选与计数的**纯逻辑**（不依赖任何 Android API，可直接 JVM 单测）。
 *
 * 背景：四个 Renderer 此前各自内联了一份「今日剩余 / 明日课程」的过滤逻辑，彼此不一致，
 * 且 Tiny 那份存在两处缺陷（见下）。此处收敛为单一实现，四个 Renderer 统一调用。
 *
 * 修复（v3.66.3）：
 * ① Tiny 是四个 Renderer 中唯一**未按开始时间排序**的，仅依赖上游 `CourseDao` 的
 *    `ORDER BY day, startSection` 隐式保证顺序；自定义时间的课（`isCustomTime=1`）在 SQL 里
 *    被排到所有节次课之后，会取错「下一节课」。此处统一显式排序。
 * ② Tiny 的剩余课数用 `todayAllCourses.size - indexOf(nextCourse)` 计算，而 `todayAllCourses`
 *    **包含已跳过的课**——只要下一节课之后还有被跳过的课，计数就会偏大。
 *    现统一用 [remainingToday] 的结果条数，语义与 Compact 一致。
 */
internal object WidgetCourseSelection {

    /**
     * `"HH:mm"` → 当日分钟数；无法解析时返回 `null`。
     *
     * 兼容 `"8:30"` / `"08:30"` / `"08:30:00"`（取前 5 位）；空串、`"99:99"` 等非法值返回 null。
     */
    fun minutesOf(time: String): Int? {
        val trimmed = time.take(5)
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val hour = trimmed.substring(0, colon).toIntOrNull() ?: return null
        val minute = trimmed.substring(colon + 1).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 今日全部课程（含已跳过、含未标注日期的模板课），按开始时间升序。 */
    fun allToday(courses: List<WidgetCourseProto>, todayStr: String): List<WidgetCourseProto> =
        courses.asSequence()
            .filter { it.date == todayStr || it.date.isBlank() }
            .sortedBy { minutesOf(it.start_time) ?: Int.MAX_VALUE }
            .toList()

    /**
     * 今日「剩余」课程：未跳过 **且** 结束时间晚于 [nowMinutes]，按开始时间升序。
     *
     * 结束时间无法解析时按「尚未结束」处理（与旧行为一致）——数据异常时宁可多显示一条，
     * 也不要让整节课从组件上消失。
     */
    fun remainingToday(
        courses: List<WidgetCourseProto>,
        todayStr: String,
        nowMinutes: Int
    ): List<WidgetCourseProto> =
        allToday(courses, todayStr).filter { course ->
            !course.is_skipped && (minutesOf(course.end_time)?.let { it > nowMinutes } ?: true)
        }

    /** 明日课程：未跳过，按开始时间升序。 */
    fun tomorrow(courses: List<WidgetCourseProto>, tomorrowStr: String): List<WidgetCourseProto> =
        courses.asSequence()
            .filter { it.date == tomorrowStr && !it.is_skipped }
            .sortedBy { minutesOf(it.start_time) ?: Int.MAX_VALUE }
            .toList()
}
