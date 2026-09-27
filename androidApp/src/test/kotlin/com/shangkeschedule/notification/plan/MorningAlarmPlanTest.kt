package com.shangkeschedule.notification.plan

import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 早八闹钟「最近一条」语义与登记簿编解码的单测。
 *
 * ## 锁死的是 2026-09-27 真机查出的第 4 条假设崩溃
 *
 * `AlarmClock.ACTION_SET_ALARM` **无法表达日期**，系统时钟把它理解为
 * 「以当前时刻为基准的**下一次**该时刻」，并建立一次性闹钟（本机实测
 * `deleteAfterUse:true`）。真机现场：
 *
 * ```
 * 请求 09-28 13:15 → 09-28 13:15   ✅（碰巧对）
 * 请求 09-29 09:45 → 09-28 09:45   ❌ 提前一天
 * 请求 09-30 07:05 → 09-28 07:05   ❌ 提前两天
 * ```
 *
 * 所以「只写最近一条」不是优化，而是**正确性前提**：写第二条必然错日期。
 * [MorningAlarmPlan.expressibleBySystemClock] 把这条判断显式化并可测。
 */
class MorningAlarmPlanTest {

    private fun alarm(
        courseDate: String,
        alarmDate: String,
        alarmHour: Int,
        alarmMinute: Int,
        name: String = "高等数学"
    ) = MorningAlarmPlan.MorningAlarm(
        courseDate = LocalDate.parse(courseDate),
        alarmDate = LocalDate.parse(alarmDate),
        alarmTime = LocalTime(alarmHour, alarmMinute),
        courseName = name,
        courseStart = LocalTime(8, 0)
    )

    // ---------------------------------------------------------------------
    // nextUpcoming
    // ---------------------------------------------------------------------

    @Test
    fun nextUpcomingPicksNearestFutureAndSkipsPast() {
        val now = LocalDateTime.parse("2026-09-27T19:52:00")
        val plan = listOf(
            alarm("2026-09-27", "2026-09-27", 7, 5, name = "已过期"),
            alarm("2026-09-29", "2026-09-29", 9, 45, name = "后天"),
            alarm("2026-09-28", "2026-09-28", 13, 15, name = "明天")
        )

        assertEquals("必须取最近的一条未来闹钟", "明天", MorningAlarmPlan.nextUpcoming(plan, now)?.courseName)
    }

    @Test
    fun nextUpcomingIsNullWhenEverythingAlreadyPassed() {
        val now = LocalDateTime.parse("2026-09-27T19:52:00")
        val plan = listOf(
            alarm("2026-09-27", "2026-09-27", 7, 5),
            alarm("2026-09-27", "2026-09-27", 13, 15)
        )

        assertNull("全部已过期时不应再写任何闹钟", MorningAlarmPlan.nextUpcoming(plan, now))
    }

    // ---------------------------------------------------------------------
    // expressibleBySystemClock
    // ---------------------------------------------------------------------

    @Test
    fun onlyNearestAlarmIsExpressibleBySystemClock() {
        val now = LocalDateTime.parse("2026-09-27T19:52:00")

        assertTrue(
            "最近一条（09-28 13:15）与系统时钟语义一致，必须允许写入",
            MorningAlarmPlan.expressibleBySystemClock(alarm("2026-09-28", "2026-09-28", 13, 15), now)
        )
        assertFalse(
            "09-29 09:45 会被系统建成 09-28 09:45，必须拒绝写入",
            MorningAlarmPlan.expressibleBySystemClock(alarm("2026-09-29", "2026-09-29", 9, 45), now)
        )
        assertFalse(
            "09-30 07:05 会被系统建成 09-28 07:05，必须拒绝写入",
            MorningAlarmPlan.expressibleBySystemClock(alarm("2026-09-30", "2026-09-30", 7, 5), now)
        )
    }

    @Test
    fun crossedMidnightAlarmIsExpressible() {
        // 课程 09-28 00:20、提前 45 分钟 → 响铃落在**前一日** 09-27 23:35。
        // 这种跨零点闹钟天然与「下一次该时刻」一致，不能被误判为不可写。
        val now = LocalDateTime.parse("2026-09-27T12:00:00")

        assertTrue(
            MorningAlarmPlan.expressibleBySystemClock(
                alarm("2026-09-28", "2026-09-27", 23, 35, name = "凌晨第一节课"),
                now
            )
        )
    }

    @Test
    fun alreadyPassedAlarmIsNotExpressible() {
        val now = LocalDateTime.parse("2026-09-27T19:52:00")

        assertFalse(
            "已经过去的时刻不该再写",
            MorningAlarmPlan.expressibleBySystemClock(alarm("2026-09-27", "2026-09-27", 7, 5), now)
        )
    }

    // ---------------------------------------------------------------------
    // MorningAlarmRegistry 编解码
    // ---------------------------------------------------------------------

    @Test
    fun registryEncodeDecodeRoundTrip() {
        val firedAt = LocalDateTime.parse("2026-09-28T13:15:00")
        val raw = MorningAlarmRegistry.encode("2026-09-28", firedAt, "早八·09-28 13:15 大学物理实验（一）")

        val decoded = MorningAlarmRegistry.decode(raw)

        assertEquals("2026-09-28" to MorningAlarmRegistry.Entry(firedAt, "早八·09-28 13:15 大学物理实验（一）"), decoded)
    }

    @Test
    fun registryDecodeDegradesLegacyFormatToUntrusted() {
        // v1（修复前）只存「课程日期 + 标签」，必须解析成功但标记为不可信，
        // 交给 prune 丢弃——否则那条错日期的闹钟会被永久固化。
        val decoded = MorningAlarmRegistry.decode("2026-09-25\u0003早八·09-25 07:05 高等数学")

        assertEquals("2026-09-25", decoded?.first)
        assertNull("v1 条目必须解析为「不可信」", decoded?.second?.firedAt)
        assertEquals("早八·09-25 07:05 高等数学", decoded?.second?.label)
    }

    @Test
    fun registryDecodeRejectsGarbage() {
        assertNull("无分隔符的脏数据应丢弃", MorningAlarmRegistry.decode("no-separator"))
        assertNull("空键应丢弃", MorningAlarmRegistry.decode("\u0003标签"))
    }

    @Test
    fun registryDecodeSurvivesUnparsableTimestamp() {
        val decoded = MorningAlarmRegistry.decode("2026-09-28\u0003not-a-datetime\u0003早八·09-28 13:15 高等数学")

        assertEquals("2026-09-28", decoded?.first)
        assertNull("时刻不可解析时退化为不可信，而不是抛异常", decoded?.second?.firedAt)
        assertEquals("早八·09-28 13:15 高等数学", decoded?.second?.label)
    }
}
