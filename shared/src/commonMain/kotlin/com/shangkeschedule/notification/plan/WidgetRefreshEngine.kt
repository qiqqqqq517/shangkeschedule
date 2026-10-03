package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * 小组件「课表边界刷新时刻」计算引擎（纯逻辑，无平台依赖，v4.67.36）。
 *
 * ## 要解决什么
 *
 * 本仓此前的小组件刷新**只有一条固定 15 分钟 tick**
 * （`WorkManagerHelper.schedulePeriodicWork` 的 `WidgetUiUpdateWorker`）。
 * 而小组件上有两类**状态型**内容会随时间翻转：
 * 「下一节课 / 正在上课」与倒计时。若用户在 10:14 上课下课，
 * 组件要等到下一个 tick（最坏 15:00 之后）才会把「正在上课」翻成「下一节课」。
 * 这段时间里用户看到的桌面是**错的**，而组件恰恰是「不用打开 App 就看课表」的地方。
 *
 * ## 借鉴
 *
 * 竞品「星链课表」的做法（`WidgetRefreshScheduler.addWeekTodayBoundaries`）：
 * 不做固定 tick，而是从课表快照反解出**当天每个状态翻转点**，
 * 用 `AlarmManager` 精确闹钟在那一刻直接刷新组件。
 *
 * ## 为什么仍然保留 15 分钟 tick
 *
 * 精确闹钟**不覆盖**下列情形，故 tick 仍是必需的兜底而非冗余：
 * 课表被改（重排会重新计算，故其实能覆盖）、用户跳课/换课表（重排覆盖）、
 * 精确闹钟权限被撤销（此时降级为不精确，见 `AlarmScheduler`）、
 * 以及**当天没有课**时的倒计时归零。
 * tick 负责「保证至少每 15 分钟正确一次」，精确闹钟负责「在关键点准时」。
 * 两者是**主路径 + 兜底**的关系，不是重复。
 *
 * ## 设计约束
 *
 * 与 [ReminderEngine] 同：本文件属 `commonMain`，不引用任何 Android / java.time API，
 * 全部用 kotlinx-datetime 表达，可在 JVM 直接单测。
 */
object WidgetRefreshEngine {

    /**
     * 每节课在**开始前**多久预刷新一次（分钟）。
     *
     * 覆盖「快上课了」这个状态：组件从「还有 X 分钟」变成「正在上课」之前，
     * 应先把倒计时刷新到位（例如 09:55 刷新出「10:00 上课」）。
     */
    const val PRE_CLASS_LEAD_MINUTES = 15

    /**
     * 每节课在**开始后**多久再刷新一次（分钟）。
     *
     * 覆盖「刚下课」：任一节课结束时刻都已由 [endMinutes] 覆盖，这里补的是
     * 「晚开始」的场景 —— 例如同一时段有 A(08:00-09:40) 与 B(08:00-09:50) 两门课，
     * 09:40 时 A 结束但 B 仍在进行中，组件不应把「正在上课」清掉；到 08:06 才翻转是错的，
     * 而 09:40 的刷新也来不及纠正它。补这一刷让「最晚结束的那门课」在结束时能触发刷新。
     */
    const val POST_CLASS_OFFSET_MINUTES = 6

    /**
     * 一天的「课程相关时段」起点之前 / 终点之后，用于夜间降频。
     *
     * 凌晨到清晨一般没有课，但**也不排除**早八（08:00 前）或夜间课程。
     * 因此这两个常量**只用于剔除明显不可能有课的时刻**，不作为「不排闹钟」的依据；
     * 真正的取舍是 [nightGapHours]：相邻两次刷新间隔超过它就跳过中间的排程。
     */
    const val NIGHT_START_HOUR = 6
    const val NIGHT_END_HOUR = 22

    /**
     * 夜间降频窗口（小时）：相邻刷新点间隔超过该值时跳过。
     *
     * 取 3 小时意味着「22:00 之后到次日 06:00 之前」这段长达 8 小时的静默期
     * 只保留首尾各一个闹钟，中间不再排 —— 既保住跨天后的首刷，
     * 又不把设备在深夜反复唤醒。
     */
    const val NIGHT_GAP_HOURS = 3

    /** 一次刷新请求。 */
    data class RefreshPoint(
        val date: LocalDate,
        val time: LocalTime,
        /** 该刷新点对应的课程 id，仅用于调试与自证（同一课程可能产生多个点）。 */
        val courseId: String
    )

    /**
     * 从课表算出小组件需要精确刷新的一天内的时刻集合。
     *
     * 每节课产生 **4 个点**（与竞品一致）：
     * `开始前 15 分` / `开始` / `开始后 6 分` / `结束`。
     *
     * - `开始前 15 分` 与 `开始`：让「即将上课 / 正在上课」准时翻转；
     * - `开始后 6 分`：覆盖「晚结束的那门课」导致的最长在读窗口（见 [POST_CLASS_OFFSET_MINUTES]）；
     * - `结束`：让「下一节课」在下课那一刻就出现，不必等 tick。
     *
     * @param courses 已由 [ReminderEngine.effectiveCourses] 过滤过的有效课程
     * @param date    只取这一天的课程
     * @return 按时刻升序去重后的刷新点；当天无有效课程时为空列表
     */
    fun refreshPoints(
        courses: List<WidgetCourse>,
        date: LocalDate
    ): List<RefreshPoint> {
        val raw = mutableListOf<RefreshPoint>()

        for (course in courses) {
            if (course.date != date.toString()) continue
            val start = ReminderEngine.parseTime(course.startTime) ?: continue

            // 结束时间缺失 / 结束不晚于开始的课程**整条丢弃**，而不是只丢结束点。
            //
            // 与 [ReminderEngine.effectiveCourses] 的 R4-001 同口径，但那里是「没有结束时间
            // 仍保留」（提醒只需开始时刻），本引擎不同：结束时刻本身就是四个刷新点之一，
            // 且它同时是「下一节课该换了」的唯一依据。放行一条 end <= start 的课，等于排下
            // 一个只会让组件跳回错误状态的闹钟（首版实现正是踩了这个坑，
            // 被 `dirtyDataIsSkipped` 抓出）。
            val end = ReminderEngine.parseTime(course.endTime)
            if (end == null || end <= start) continue

            // 开始 / 开始前 15 分
            raw += RefreshPoint(date, start, course.id)
            val pre = ReminderEngine.shiftMinutes(start, -PRE_CLASS_LEAD_MINUTES)
            if (pre != null && !pre.crossedBackward) {
                // 跨到前一天的点交给前一天的排程负责，不在本日重复排（否则会漏）
                raw += RefreshPoint(date, pre.time, course.id)
            }

            // 开始后 6 分
            val post = ReminderEngine.shiftMinutes(start, POST_CLASS_OFFSET_MINUTES)
            if (post != null) {
                raw += RefreshPoint(date, post.time, course.id)
            }

            // 结束时刻
            raw += RefreshPoint(date, end, course.id)
        }

        // 去重（同一时刻可能多门课）+ 排序；同一时刻不同课程没有区别，渲染结果一致。
        return raw
            .distinctBy { it.time }
            .sortedBy { it.time }
    }

    /**
     * 对刷新点序列做**夜间降频**：间隔超过 [NIGHT_GAP_HOURS] 的相邻两点之间，
     * 跳过中间点，只保留首尾。
     *
     * 为什么不直接按 [NIGHT_START_HOUR] / [NIGHT_END_HOUR] 过滤：
     * 那两个常量描述的是「课可能在哪几个小时」，而早八（08:00 前）、
     * 夜间选修课都真实存在。一刀切按小时过滤会**漏排**真实存在的课，
     * 那比多排几个闹钟严重得多。按间隔降频则与课表内容无关，
     * 无论用户在哪个时段上课都一定保留它的首尾两个刷新点。
     *
     * 白天空档（如 12:00→14:00）间隔只有 2 小时 < 3，**不会**被降频 ——
     * 这正是我们想要的：课间仍要准时刷新。
     */
    fun thinNightGaps(points: List<RefreshPoint>): List<RefreshPoint> {
        if (points.size <= 2) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.lastIndex) {
            val prev = out.last()
            val current = points[i]
            val gapHours = current.time.hour - prev.time.hour
            // 同小时（分钟级间隔）必然保留；仅当「跨了足够多的整小时」才丢弃中间点。
            if (gapHours < NIGHT_GAP_HOURS) out += current
        }
        out += points.last()
        return out
    }

    /**
     * 排程用：一天内最终要挂精确闹钟的刷新点（已排序）。
     *
     * = [refreshPoints] + [thinNightGaps]，两步分开的目的是让两者都能独立单测。
     */
    fun scheduleFor(
        courses: List<WidgetCourse>,
        date: LocalDate
    ): List<RefreshPoint> = thinNightGaps(refreshPoints(courses, date))
}
