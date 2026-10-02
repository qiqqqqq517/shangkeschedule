import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.notification.identity.AlarmCodeBook
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.identity.NotificationIds
import com.shangkeschedule.notification.plan.ReminderEngine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 通知子系统核心逻辑回归测试。
 *
 * 覆盖三块：
 *  1. [NotificationIds]：occurrence 稳定通知 ID（幂等 / 不碰撞 / 不落保留区间）
 *  2. [ReminderEngine]：自动模式切换序列与状态校准、灵动岛窗口、时间偏移跨零点
 *  3. [MorningAlarmPlan]：早八闹钟计划（取最早课 / 跳过日 / 提前量边界 / 跨零点）
 *
 * 这些断言替代了旧实现「无任何测试覆盖」的状态——旧三处调度逻辑
 * （CourseNotificationWorker / DndSchedulerWorker / DynamicIslandManager）
 * 都嵌在 Android framework 与 Room 里，无法单测，也正是缺陷长期潜伏的原因。
 */
class NotificationEngineTest {

    private fun course(
        id: String,
        date: String,
        start: String,
        end: String,
        name: String = "高等数学",
        skipped: Boolean = false
    ) = WidgetCourse(
        id = id,
        name = name,
        teacher = "张老师",
        position = "A101",
        startTime = start,
        endTime = end,
        isSkipped = skipped,
        date = date,
        colorInt = 0
    )

    // ------------------------------------------------------------------
    // 1. 通知 ID
    // ------------------------------------------------------------------

    @Test
    fun notificationIdIsStableForSameOccurrence() {
        val a = course("c1", "2026-09-28", "08:00", "08:45")
        val b = course("c1", "2026-09-28", "08:00", "08:45") // 内容相同
        assertEquals(
            NotificationIds.forOccurrence(a),
            NotificationIds.forOccurrence(b),
            "同一 occurrence 必须得到相同通知 ID（幂等更新，而非新建）"
        )
    }

    @Test
    fun notificationIdDiffersForDifferentOccurrence() {
        val base = course("c1", "2026-09-28", "08:00", "08:45")
        val otherDay = course("c1", "2026-09-29", "08:00", "08:45")
        val otherCourse = course("c2", "2026-09-28", "08:00", "08:45")
        val otherSlot = course("c1", "2026-09-28", "10:00", "10:45")

        val ids = listOf(base, otherDay, otherCourse, otherSlot).map { NotificationIds.forOccurrence(it) }
        assertEquals(
            ids.size,
            ids.toSet().size,
            "课程/日期/节次任一不同都必须是不同通知 ID，否则通知会互相顶替（旧实现缺陷 #1）"
        )
    }

    @Test
    fun notificationIdNeverFallsInReservedRanges() {
        // 批量生成，确认命名空间与旧槽位区间、灵动岛通知 ID 完全隔离
        val ids = (0 until 500).map { index ->
            NotificationIds.forOccurrence("course-$index", "2026-09-28", "08:00")
        }
        for (id in ids) {
            assertTrue(
                id !in NotificationIds.RESERVED_ID_RANGE,
                "通知 ID $id 落进了旧槽位保留区间 ${NotificationIds.RESERVED_ID_RANGE}"
            )
            assertNotEquals(NotificationIds.DYNAMIC_ISLAND_ID, id, "不得与灵动岛通知 ID 冲突")
            assertTrue(NotificationIds.isOwned(id), "通知 ID $id 应属本命名空间")
        }
    }

    @Test
    fun notificationIdsArePositive() {
        // 负数通知 ID 在 NotificationManager 上是非法输入
        val ids = (0 until 300).map { NotificationIds.forOccurrence("k$it") }
        assertTrue(ids.all { it > 0 }, "所有通知 ID 必须为正数")
    }

    @Test
    fun notificationIdHasNoCollisionAcrossRealisticWeekWindow() {
        // v4.64.21 回归：命名空间容量由 10 万提到 100 万（10 万槽下 7 天窗口实测约
        // 2.2% 会撞）。碰撞后果是两条课程共用一个通知 ID —— 后投递的顶替先投递的，
        // dismiss PendingIntent 还会连带把另一条一起关掉。
        //
        // 这里构造真实排程量级：7 天 × 每天 10 节 = 70 个 occurrence，跨多门课。
        val ids = buildSet {
            for (courseIndex in 0 until 4) {
                for (dayOffset in 0 until 7) {
                    for (slot in 0 until 10) {
                        val date = LocalDate(2026, 9, 28).plus(dayOffset.toLong(), DateTimeUnit.DAY)
                        add(
                            NotificationIds.forOccurrence(
                                "c$courseIndex",
                                date.toString(),
                                "%02d:00".format(8 + slot)
                            )
                        )
                    }
                }
            }
        }
        val total = 4 * 7 * 10
        assertEquals(
            total,
            ids.size,
            "7 天 × 10 节 × 4 门课（$total 个 occurrence）必须全部拿到互不相同的通知 ID"
        )
    }

    // ------------------------------------------------------------------
    // 2. AlarmCodeBook：请求码分配
    // ------------------------------------------------------------------

    @Test
    fun alarmCodeBookAssignsUniqueCodes() {
        val book = AlarmCodeBook(base = 60_000, capacity = 10)
        val codes = (0 until 10).map { book.codeFor("key-$it") }
        assertEquals(10, codes.toSet().size, "同一轮内请求码必须互不相同")
        assertEquals(10, book.size)
    }

    @Test
    fun alarmCodeBookReusesCodeForKey() {
        val book = AlarmCodeBook()
        val first = book.codeFor("same-key")
        val again = book.codeFor("same-key")
        assertEquals(first, again, "同一键重复取码应复用，避免要取消再重建闹钟")
        assertEquals(1, book.size)
    }

    @Test
    fun alarmCodeBookReturnsNullWhenExhausted() {
        val book = AlarmCodeBook(capacity = 2)
        book.codeFor("a"); book.codeFor("b")
        assertNull(book.codeFor("c"), "超出容量应返回 null，由调用方决定截断策略")
    }

    @Test
    fun alarmCodeBookResetClearsAssignments() {
        val book = AlarmCodeBook(capacity = 2)
        book.codeFor("a"); book.codeFor("b")
        book.reset()
        assertEquals(0, book.size)
        assertEquals(60_000, book.codeFor("z"), "reset 后应从基址重新开始分配")
    }

    // ------------------------------------------------------------------
    // 3. ReminderEngine：时间解析与偏移
    // ------------------------------------------------------------------

    @Test
    fun parseTimeAcceptsPaddedAndUnpadded() {
        assertEquals(LocalTime(8, 0), ReminderEngine.parseTime("08:00"))
        assertEquals(LocalTime(8, 5), ReminderEngine.parseTime("8:5"))
        assertEquals(LocalTime(23, 59), ReminderEngine.parseTime("23:59"))
    }

    @Test
    fun parseTimeRejectsInvalid() {
        assertNull(ReminderEngine.parseTime(null))
        assertNull(ReminderEngine.parseTime(""))
        assertNull(ReminderEngine.parseTime("abc"))
        assertNull(ReminderEngine.parseTime("08"))
        assertNull(ReminderEngine.parseTime("25:00"))
    }

    @Test
    fun parseTimeAcceptsIsoFormWithSeconds() {
        // LocalTime.parse 接受完整 ISO 形式；保持宽容比拒绝更安全
        // （与旧 DynamicIslandManager.parseTime 行为一致，不引入回归）
        assertEquals(LocalTime(8, 0), ReminderEngine.parseTime("08:00:00"))
    }

    @Test
    fun shiftMinutesHandlesCrossMidnight() {
        // 00:20 提前 45 分钟 → 前一天 23:35
        val shifted = ReminderEngine.shiftMinutes(LocalTime(0, 20), -45)
        assertEquals(LocalTime(23, 35), shifted?.time)
        assertTrue(shifted!!.crossedBackward, "应标记为向前跨天")
        assertTrue(shifted.crossedDay)
    }

    @Test
    fun shiftMinutesForwardCrossesDay() {
        val shifted = ReminderEngine.shiftMinutes(LocalTime(23, 50), 30)
        assertEquals(LocalTime(0, 20), shifted?.time)
        assertTrue(shifted!!.crossedForward)
    }

    @Test
    fun shiftReturnsAbsoluteDate() {
        // 凌晨 00:20 的课提前 45 分钟 → 闹钟落在前一天
        val (date, time) = ReminderEngine.shift(LocalDate(2026, 9, 28), LocalTime(0, 20), -45)!!
        assertEquals(LocalDate(2026, 9, 27), date, "跨零点必须回退到前一天日期")
        assertEquals(LocalTime(23, 35), time)
    }

    // ------------------------------------------------------------------
    // 4. ReminderEngine：自动勿扰 / 静音
    // ------------------------------------------------------------------

    @Test
    fun autoModeTransitionsProducesStartAndEndPerCourse() {
        val courses = listOf(
            course("c1", "2026-09-28", "08:00", "08:45"),
            course("c2", "2026-09-28", "10:00", "10:45")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(4, transitions.size)
        assertEquals(LocalTime(8, 0), transitions[0].time)
        assertTrue(transitions[0].enable)
        assertEquals(LocalTime(8, 45), transitions[1].time)
        assertTrue(!transitions[1].enable, "下课应关闭自动模式")
        assertEquals(LocalTime(10, 0), transitions[2].time)
        assertTrue(transitions[2].enable)
        assertEquals(LocalTime(10, 45), transitions[3].time)
        assertTrue(!transitions[3].enable)
    }

    @Test
    fun autoModeTransitionsMergeBackToBackCourses() {
        // 第一节 08:45 下课、第二节 08:45 上课：不应在课间闪回正常铃声
        val courses = listOf(
            course("c1", "2026-09-28", "08:00", "08:45"),
            course("c2", "2026-09-28", "08:45", "09:30")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(2, transitions.size, "同刻的「关-开」应合并为一次开启")
        assertEquals(LocalTime(8, 0), transitions[0].time)
        assertTrue(transitions[0].enable)
        assertEquals(LocalTime(9, 30), transitions[1].time)
        assertTrue(!transitions[1].enable)
    }

    // ------------------------------------------------------------------
    // 重叠课程的回归用例（XL-001）
    //
    // 旧实现按「转换点」归一化，遇到重叠会丢掉较晚的结束时间：
    //   A 11:00-12:00 + B 11:30-13:00 → 「相邻同态去重」丢掉 关13:00
    //   → 只剩 开11:00 / 关12:00 → 12:00 恢复铃声，而 B 上到 13:00
    // 现改为合并「区间」，重叠与紧邻统一正确。
    // ------------------------------------------------------------------

    @Test
    fun autoModeTransitionsMergeOverlappingCourses() {
        val courses = listOf(
            course("a", "2026-09-28", "11:00", "12:00"),
            course("b", "2026-09-28", "11:30", "13:00")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(2, transitions.size, "重叠课程应合并成一段静音区间")
        assertEquals(LocalTime(11, 0), transitions[0].time, "起点取最早")
        assertTrue(transitions[0].enable)
        assertEquals(LocalTime(13, 0), transitions[1].time, "终点取最晚，不能被前一段截断")
        assertTrue(!transitions[1].enable)
    }

    @Test
    fun autoModeTransitionsMergeFullyNestedCourse() {
        // B 完全落在 A 之内：区间应取 A 的外沿，不能被 B 缩短
        val courses = listOf(
            course("a", "2026-09-28", "11:00", "13:00"),
            course("b", "2026-09-28", "11:30", "12:00")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(2, transitions.size)
        assertEquals(LocalTime(11, 0), transitions[0].time)
        assertEquals(LocalTime(13, 0), transitions[1].time)
        assertTrue(!transitions[1].enable)
    }

    @Test
    fun autoModeTransitionsKeepChainOfOverlaps() {
        // 三段链式重叠 A→B→C：合并后应只剩一对开关
        val courses = listOf(
            course("a", "2026-09-28", "09:00", "11:00"),
            course("b", "2026-09-28", "10:30", "12:00"),
            course("c", "2026-09-28", "11:45", "14:00")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(2, transitions.size, "链式重叠应合并为一段")
        assertEquals(LocalTime(9, 0), transitions[0].time)
        assertEquals(LocalTime(14, 0), transitions[1].time)
        assertTrue(!transitions[1].enable)
    }

    @Test
    fun autoModeTransitionsKeepSeparateDaysIndependent() {
        val courses = listOf(
            course("a", "2026-09-28", "11:00", "12:00"),
            course("b", "2026-09-29", "11:30", "13:00")
        )
        val transitions = ReminderEngine.autoModeTransitions(courses)
        assertEquals(4, transitions.size, "跨天的区间不得互相合并")
        assertEquals(LocalDate(2026, 9, 28), transitions[0].date)
        assertEquals(LocalDate(2026, 9, 29), transitions[2].date)
    }

    @Test
    fun autoModeTransitionsNeverEndBeforeStart() {
        // 结束早于开始的脏数据：effectiveCourses 已过滤，此处兜底确认序列仍自洽
        val courses = listOf(course("bad", "2026-09-28", "12:00", "11:00"))
        val transitions = ReminderEngine.autoModeTransitions(courses)
        transitions.zipWithNext { a, b ->
            assertTrue(
                a.time < b.time || a.date < b.date,
                "开启必须早于关闭：$a -> $b"
            )
        }
    }

    @Test
    fun shouldModeBeOnDetectsInClass() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45"))
        val date = LocalDate(2026, 9, 28)
        assertTrue(
            ReminderEngine.shouldModeBeOn(courses, emptySet(), date, LocalTime(8, 30)),
            "课中应处于开启态"
        )
        assertTrue(
            ReminderEngine.shouldModeBeOn(courses, emptySet(), date, LocalTime(8, 0)),
            "上课瞬间（含左端点）应开启"
        )
        assertTrue(
            !ReminderEngine.shouldModeBeOn(courses, emptySet(), date, LocalTime(8, 46)),
            "下课之后应关闭（右端点不含）"
        )
        assertTrue(
            !ReminderEngine.shouldModeBeOn(courses, emptySet(), date, LocalTime(7, 59)),
            "上课之前不应开启"
        )
    }

    @Test
    fun shouldModeBeOnIgnoresSkippedDatesAndCourses() {
        val courses = listOf(
            course("c1", "2026-09-28", "08:00", "08:45"),
            course("c2", "2026-09-29", "08:00", "08:45", skipped = true)
        )
        val date = LocalDate(2026, 9, 28)
        assertTrue(
            !ReminderEngine.shouldModeBeOn(courses, setOf("2026-09-28"), date, LocalTime(8, 30)),
            "整体被跳过的日期不应开启自动模式"
        )
        assertTrue(
            !ReminderEngine.shouldModeBeOn(
                courses, emptySet(), LocalDate(2026, 9, 29), LocalTime(8, 30)
            ),
            "单节 isSkipped 的课程不参与自动模式"
        )
    }

    // ------------------------------------------------------------------
    // 5. ReminderEngine：灵动岛窗口
    // ------------------------------------------------------------------

    @Test
    fun islandWindowUsesFirstStartMinusLeadAndLastEnd() {
        val courses = listOf(
            course("c1", "2026-09-28", "10:00", "10:45"),
            course("c2", "2026-09-28", "08:00", "08:45")
        )
        val window = ReminderEngine.islandWindow(courses, emptySet(), LocalDate(2026, 9, 28), 15)!!
        assertEquals(LocalTime(7, 45), window.start, "窗口起点 = 第一节 08:00 − 15 分钟")
        assertEquals(LocalTime(10, 45), window.end, "窗口终点 = 最后一节结束")
    }

    @Test
    fun islandWindowNullOnDayWithoutCourses() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45"))
        assertNull(
            ReminderEngine.islandWindow(courses, emptySet(), LocalDate(2026, 9, 29), 15),
            "当天无课应返回 null，而不是造出一个空窗口"
        )
    }

    // ------------------------------------------------------------------
    // 6. MorningAlarmPlan：早八闹钟
    // ------------------------------------------------------------------

    @Test
    fun morningAlarmUsesEarliestCourseOfDay() {
        val courses = listOf(
            course("c1", "2026-09-28", "10:00", "10:45", name = "大学物理"),
            course("c2", "2026-09-28", "08:00", "08:45", name = "高等数学")
        )
        val plan = MorningAlarmPlan.plan(
            courses, today = LocalDate(2026, 9, 28), days = 1, leadMinutes = 45
        )
        assertEquals(1, plan.size)
        assertEquals("高等数学", plan[0].courseName, "应取当天最早的一节课")
        assertEquals(LocalTime(7, 15), plan[0].alarmTime, "08:00 − 45 分钟 = 07:15")
        assertEquals(LocalTime(8, 0), plan[0].courseStart)
    }

    @Test
    fun morningAlarmHonoursLeadBoundaries() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45"))
        val today = LocalDate(2026, 9, 28)

        // 0 分钟 = 正点
        assertEquals(
            LocalTime(8, 0),
            MorningAlarmPlan.plan(courses, today = today, days = 1, leadMinutes = 0)[0].alarmTime
        )
        // 45 分钟
        assertEquals(
            LocalTime(7, 15),
            MorningAlarmPlan.plan(courses, today = today, days = 1, leadMinutes = 45)[0].alarmTime
        )
        // 180 分钟（上限）
        assertEquals(
            LocalTime(5, 0),
            MorningAlarmPlan.plan(courses, today = today, days = 1, leadMinutes = 180)[0].alarmTime
        )
        // 超上限应收敛到 180，而不是算出离谱时刻
        assertEquals(
            LocalTime(5, 0),
            MorningAlarmPlan.plan(courses, today = today, days = 1, leadMinutes = 999)[0].alarmTime
        )
    }

    @Test
    fun morningAlarmSkipsDaysWithoutCourses() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45"))
        val plan = MorningAlarmPlan.plan(
            courses, today = LocalDate(2026, 9, 28), days = 3, leadMinutes = 45
        )
        assertEquals(1, plan.size, "只有 09-28 有课，另外两天不应生成闹钟")
        assertEquals(LocalDate(2026, 9, 28), plan[0].courseDate)
    }

    @Test
    fun morningAlarmSkipsSkippedDatesAndCourses() {
        val courses = listOf(
            course("c1", "2026-09-28", "08:00", "08:45"),
            course("c2", "2026-09-29", "08:00", "08:45", skipped = true)
        )
        val plan = MorningAlarmPlan.plan(
            courses,
            skippedDates = setOf("2026-09-30"),
            today = LocalDate(2026, 9, 28),
            days = 4,
            leadMinutes = 45
        )
        assertEquals(1, plan.size, "跳过日与 isSkipped 课程都不应生成闹钟")
        assertEquals(LocalDate(2026, 9, 28), plan[0].courseDate)
    }

    @Test
    fun morningAlarmHandlesMidnightCrossing() {
        // 00:20 的早课提前 45 分钟 → 闹钟落在前一天 23:35
        val courses = listOf(course("c1", "2026-09-28", "00:20", "01:05"))
        val plan = MorningAlarmPlan.plan(
            courses, today = LocalDate(2026, 9, 28), days = 1, leadMinutes = 45
        )
        assertEquals(1, plan.size)
        assertEquals(LocalDate(2026, 9, 27), plan[0].alarmDate, "跨零点应回退到前一天")
        assertEquals(LocalTime(23, 35), plan[0].alarmTime)
        assertTrue(plan[0].crossedMidnight)
    }

    @Test
    fun morningAlarmLabelCarriesDateAndTime() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45", name = "高等数学"))
        val alarm = MorningAlarmPlan.plan(
            courses, today = LocalDate(2026, 9, 28), days = 1, leadMinutes = 45
        )[0]
        val label = MorningAlarmPlan.label(alarm)
        // 标签需带完整日期与时刻，供用户在系统闹钟页辨认 + 删除时精确搜索
        assertTrue(label.contains("09-28"), "标签应含日期：$label")
        assertTrue(label.contains("07:15"), "标签应含响铃时刻：$label")
        assertTrue(label.contains("高等数学"), "标签应含课程名：$label")
    }

    @Test
    fun morningAlarmSkipsMalformedCourses() {
        // 结束不晚于开始 = 脏数据，不应生成闹钟
        val courses = listOf(course("c1", "2026-09-28", "08:45", "08:00"))
        assertTrue(
            MorningAlarmPlan.plan(
                courses, today = LocalDate(2026, 9, 28), days = 1, leadMinutes = 45
            ).isEmpty(),
            "时间区间异常的课程不参与早八计划"
        )
    }

    @Test
    fun morningAlarmEmptyForNonPositiveDays() {
        val courses = listOf(course("c1", "2026-09-28", "08:00", "08:45"))
        assertTrue(
            MorningAlarmPlan.plan(courses, today = LocalDate(2026, 9, 28), days = 0).isEmpty()
        )
    }
}
