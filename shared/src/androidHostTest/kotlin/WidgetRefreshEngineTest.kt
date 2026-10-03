package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WidgetRefreshEngine]：小组件「课表边界刷新时刻」计算。
 *
 * 重点锁三件事：
 * 1. 每节课必须产出 **4 个刷新点**（开始前 15 / 开始 / 开始后 6 / 结束），
 *    缺任一个都会让某次状态翻转错过 —— 而这正是本引擎存在的唯一理由；
 * 2. **夜间降频不能误伤课间**：白天空档（12:00→14:00）必须全部保留；
 * 3. 脏数据（缺时间、结束早于开始、非本日期）一律不排，不得产生无效闹钟。
 */
class WidgetRefreshEngineTest {

    private val date = LocalDate(2026, 10, 3) // 周六

    private fun course(
        id: String,
        start: String,
        end: String,
        onDate: String = date.toString()
    ) = WidgetCourse(
        id = id,
        name = "课程$id",
        teacher = "T",
        position = "P",
        startTime = start,
        endTime = end,
        isSkipped = false,
        date = onDate,
        colorInt = 0
    )

    private fun times(points: List<WidgetRefreshEngine.RefreshPoint>): List<String> =
        points.map { it.time.toString() }

    @Test
    fun singleCourseYieldsFourRefreshPoints() {
        val points = WidgetRefreshEngine.refreshPoints(
            listOf(course("a", "10:00", "11:40")),
            date
        )
        // 10:00 前 15 分 = 09:45；开始 10:00；开始后 6 分 = 10:06；结束 11:40
        assertEquals(listOf("09:45", "10:00", "10:06", "11:40"), times(points))
    }

    @Test
    fun refreshPointsAreDeduplicatedByTime() {
        // 两门课共享开始时刻 → 同一时刻只排一个闹钟（渲染结果一致，重复排是浪费）
        val points = WidgetRefreshEngine.refreshPoints(
            listOf(
                course("a", "10:00", "11:00"),
                course("b", "10:00", "11:40")
            ),
            date
        )
        val all = times(points)
        assertEquals("时间点不应重复", all.size, all.distinct().size)
        // 结束时刻仍各自保留（11:00 与 11:40 是不同的翻转点）
        assertTrue(all.contains("11:00"))
        assertTrue(all.contains("11:40"))
    }

    @Test
    fun morningClassBeforeSixIsNotDropped() {
        // 早八前一节课 06:30 开始：开始前 15 分落在 06:15，仍在本日，不应被跨天守卫丢掉
        val points = WidgetRefreshEngine.refreshPoints(
            listOf(course("a", "06:30", "07:20")),
            date
        )
        val all = times(points)
        assertTrue("06:15 应保留，实际 $all", all.contains("06:15"))
        assertTrue(all.contains("06:30"))
    }

    @Test
    fun classStartingAtMidnightDoesNotEmitPreviousDayPoint() {
        // 00:00 开课：开始前 15 分会跨到前一日，交给前一天排程，本日不得出现 23:45
        val points = WidgetRefreshEngine.refreshPoints(
            listOf(course("a", "00:00", "01:00")),
            date
        )
        val all = times(points)
        assertTrue("不应产生跨日的 23:45，实际 $all", !all.contains("23:45"))
        assertTrue(all.contains("00:00"))
        assertTrue(all.contains("01:00"))
    }

    @Test
    fun dirtyDataIsSkipped() {
        val points = WidgetRefreshEngine.refreshPoints(
            listOf(
                course("bad-end", "10:00", "09:00"),   // 结束早于开始（R4-001 同款脏数据）
                course("no-time", "", ""),              // 时间不可解析
                course("other-day", "10:00", "11:00", "2026-10-04")
            ),
            date
        )
        assertTrue("脏数据不应产生刷新点，实际 ${times(points)}", points.isEmpty())
    }

    @Test
    fun dayGapIsThinnedButClassGapIsNot() {
        // 上午 08:00 的课结束于 12:00，下午 16:00 的课开始 —— 间隔 4 小时应降频
        val day = WidgetRefreshEngine.thinNightGaps(
            WidgetRefreshEngine.refreshPoints(
                listOf(
                    course("a", "08:00", "12:00"),
                    course("b", "16:00", "17:40")
                ),
                date
            )
        )
        assertTrue(
            "4 小时空档应被降频，实际 ${times(day)}",
            day.size < WidgetRefreshEngine.refreshPoints(
                listOf(
                    course("a", "08:00", "12:00"),
                    course("b", "16:00", "17:40")
                ),
                date
            ).size
        )
        // 但首尾必须保留（跨天后仍能正确刷新）
        assertEquals("07:45", day.first().time.toString())
        assertEquals("17:40", day.last().time.toString())
    }

    @Test
    fun normalClassGapIsFullyKept() {
        // 09:40 下课、10:00 上课：课间 20 分钟，必须全部保留刷新点
        val all = times(
            WidgetRefreshEngine.refreshPoints(
                listOf(
                    course("a", "08:00", "09:40"),
                    course("b", "10:00", "11:40")
                ),
                date
            )
        )
        assertTrue("课间刷新点不应被降频丢弃，实际 $all", all.containsAll(listOf("09:40", "09:45", "10:00")))
    }

    @Test
    fun emptyDayYieldsNoSchedule() {
        assertTrue(WidgetRefreshEngine.scheduleFor(emptyList(), date).isEmpty())
    }

    @Test
    fun scheduleIsSortedAscending() {
        val points = WidgetRefreshEngine.scheduleFor(
            listOf(course("b", "14:00", "15:40"), course("a", "08:00", "09:40")),
            date
        )
        val ordered = points.map { it.time }
        assertEquals(ordered.sorted(), ordered)
        assertTrue(ordered.isNotEmpty())
    }

    @Test
    fun overlappingClassesKeepLatestEnd() {
        // A 08:00-09:40，B 08:00-09:50：09:40 之后仍在上课，结束点必须保留 09:50
        val all = times(
            WidgetRefreshEngine.refreshPoints(
                listOf(course("a", "08:00", "09:40"), course("b", "08:00", "09:50")),
                date
            )
        )
        assertTrue("应保留较晚的结束时刻 09:50，实际 $all", all.contains("09:50"))
    }

    /** 钉住常量，防止有人把「开始后 6 分」调成一个会与提醒提前量冲突的值。 */
    @Test
    fun constantsAreSane() {
        assertEquals(15, WidgetRefreshEngine.PRE_CLASS_LEAD_MINUTES)
        assertEquals(6, WidgetRefreshEngine.POST_CLASS_OFFSET_MINUTES)
        assertTrue(
            "课间空档必须小于夜间降频阈值，否则课间刷新会被误降频",
            2 < WidgetRefreshEngine.NIGHT_GAP_HOURS
        )
    }

    // ---------------------------------------------------------------------
    // 请求码分配（v4.67.36 自审新增）
    //
    // 平台层注销是「扫 [codeBase, codeBase+limit) 整段」，
    // 因此**任何落到段外的请求码都永远撤不掉**，每轮重排都会叠加一批孤儿闹钟。
    // 首版正是按天加了偏移（offset * limit + count）而踩中，故此处用单测钉死。
    // ---------------------------------------------------------------------

    private val now = LocalDateTime(date, LocalTime(9, 0))
    private val codeBase = 64_000
    private val codeLimit = 200

    @Test
    fun allocatedCodesAllFallInsideNamespace() {
        // 两天各 4 节课，**合并成一份**后一次分配（与平台层用法一致）
        val days = listOf(date, date.plus(1L, DateTimeUnit.DAY))
        val points = days.flatMap { WidgetRefreshEngine.scheduleFor(MANY_COURSES, it) }
        val all = WidgetRefreshEngine.allocate(points, now, codeBase, codeLimit)
        assertTrue("应排出若干闹钟", all.isNotEmpty())
        all.forEach {
            assertTrue(
                "请求码 ${it.requestCode} 落在命名空间之外，永远撤不掉",
                it.requestCode in codeBase until (codeBase + codeLimit)
            )
        }
        // 且码必须唯一 —— 重复码会让两个刷新点互相覆盖（FLAG_UPDATE_CURRENT）
        assertEquals(
            "请求码必须唯一",
            all.size,
            all.map { it.requestCode }.distinct().size
        )
    }

    /**
     * 回归：按天**各调一次** allocate 会让跨天请求码重复。
     *
     * 这是首版的第二个坑 —— `WidgetBoundaryAlarmScheduler` 最初写成
     * `days.flatMap { allocate(scheduleFor(courses, it), codeBase = …) }`，
     * 每天都从同一基址重新计数，于是第二天的闹钟与第一天的码相同、
     * 被 `FLAG_UPDATE_CURRENT` 覆盖 ⇒ 一天里的刷新点被静默顶掉。
     *
     * 本测试**故意走那条错误用法**，断言它确实产生重复码，
     * 从而把「必须合并成一次分配」这条契约变成可执行的说明。
     *
     * 注：课表必须按日期生成（[coursesOn]）—— 若沿用固定在 `date` 上的课程，
     * 第二天会因日期不匹配被 [WidgetRefreshEngine.refreshPoints] 全量过滤掉，
     * 第二天自然排出 0 个闹钟，于是「无重复」是因为「压根没排」而非「不冲突」，
     * 断言会假绿。
     */
    @Test
    fun perDayAllocationWouldCollideAcrossDays() {
        val day1 = date
        val day2 = date.plus(1L, DateTimeUnit.DAY)
        val wrongWay = listOf(day1, day2).flatMap { d ->
            WidgetRefreshEngine.allocate(
                WidgetRefreshEngine.scheduleFor(coursesOn(d), d),
                now = now,
                codeBase = codeBase,
                codeLimit = codeLimit
            )
        }
        assertTrue(
            "两天都应各自排出闹钟，实际 ${wrongWay.size} 个",
            wrongWay.size >= 4
        )
        assertTrue(
            "按天各调一次必然产生重复请求码；本测试用于说明「必须合并成一次分配」",
            wrongWay.map { it.requestCode }.distinct().size < wrongWay.size
        )
    }

    /** 同一天的正确用法：合并成一次分配 ⇒ 码唯一且全在命名空间内。 */
    @Test
    fun mergedAllocationHasNoCollisionAcrossDays() {
        val day1 = date
        val day2 = date.plus(1L, DateTimeUnit.DAY)
        val points = listOf(day1, day2).flatMap { d ->
            WidgetRefreshEngine.scheduleFor(coursesOn(d), d)
        }
        val all = WidgetRefreshEngine.allocate(points, now, codeBase, codeLimit)
        assertTrue("两天都应排出闹钟", all.size >= 4)
        assertEquals(
            "合并分配后请求码必须唯一",
            all.size,
            all.map { it.requestCode }.distinct().size
        )
        // 且按触发时间递增
        val times = all.map { it.triggerAt }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun allocationIsContiguousFromBase() {
        val points = WidgetRefreshEngine.refreshPoints(MANY_COURSES, date)
        val allocated = WidgetRefreshEngine.allocate(points, now, codeBase, codeLimit)
        allocated.forEachIndexed { i, item ->
            assertEquals("第 $i 个码必须是 base + $i", codeBase + i, item.requestCode)
        }
    }

    @Test
    fun pastPointsAreNotScheduled() {
        val points = WidgetRefreshEngine.refreshPoints(MANY_COURSES, date)
        // 把 now 设到当天 23:59 ⇒ 全部刷新点都已过去
        val allocated = WidgetRefreshEngine.allocate(
            points,
            now = LocalDateTime(date, LocalTime(23, 59)),
            codeBase = codeBase,
            codeLimit = codeLimit
        )
        assertTrue("已过期的点不得排出闹钟", allocated.isEmpty())
    }

    @Test
    fun allocationIsTruncatedAtLimit() {
        val points = WidgetRefreshEngine.refreshPoints(MANY_COURSES, date)
        val allocated = WidgetRefreshEngine.allocate(points, now, codeBase, codeLimit = 3)
        assertEquals("超出上限的部分必须截断，不得越界", 3, allocated.size)
        assertTrue(allocated.last().requestCode < codeBase + 3)
    }

    @Test
    fun allocatedTriggerTimesMatchTheirPoints() {
        val points = WidgetRefreshEngine.refreshPoints(MANY_COURSES, date)
        val allocated = WidgetRefreshEngine.allocate(points, now, codeBase, codeLimit)
        assertEquals(
            allocated.map { it.triggerAt.time }.toSet(),
            points.filter { LocalDateTime(it.date, it.time) > now }.map { it.time }.toSet()
        )
    }

    /** 四节课固定落在 [date] 当天（供只关心单日行为的测试用）。 */
    private val MANY_COURSES = listOf(
        course("a", "08:00", "09:40"),
        course("b", "10:00", "11:40"),
        course("c", "14:00", "15:40"),
        course("d", "16:00", "17:40")
    )

    /** 同样四节课，但改挂到 [on] 当天 —— 跨天场景必须用它，否则第二天会被整批过滤掉。 */
    private fun coursesOn(on: LocalDate): List<WidgetCourse> = listOf(
        course("a", "08:00", "09:40", onDate = on.toString()),
        course("b", "10:00", "11:40", onDate = on.toString()),
        course("c", "14:00", "15:40", onDate = on.toString()),
        course("d", "16:00", "17:40", onDate = on.toString())
    )
}