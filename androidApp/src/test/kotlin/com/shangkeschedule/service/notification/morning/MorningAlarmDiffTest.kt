package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 早八闹钟差分逻辑单测。
 *
 * ## 为什么要锁「不重写」这个行为
 *
 * 真机实测（Xiaomi 22041216UC / MIUI V816 / Android 14）推翻了原设计的两条假设：
 *  1. `ACTION_DISMISS_ALARM`（LABEL 与 TIME 两种搜索模式）**不删除闹钟**，
 *     只把时钟界面调到前台；
 *  2. `ACTION_SET_ALARM` **非幂等**——同一时刻+同一标签连发两次会留下**两条**重复闹钟。
 *
 * 在「删不掉」+「写了就重复」的双重约束下，唯一安全策略是**每个日期只写一次**：
 * 已登记过的日期绝不重写，哪怕标签变了（把最终调整权交给用户）。
 * 本测试锁死这个契约——它直接决定用户的系统闹钟列表会不会被污染。
 */
class MorningAlarmDiffTest {

    private fun alarm(
        date: String,
        startHour: Int,
        startMinute: Int,
        alarmHour: Int,
        alarmMinute: Int,
        name: String = "高等数学"
    ) = MorningAlarmPlan.MorningAlarm(
        courseDate = LocalDate.parse(date),
        alarmDate = LocalDate.parse(date),
        alarmTime = LocalTime(alarmHour, alarmMinute),
        courseName = name,
        courseStart = LocalTime(startHour, startMinute)
    )

    @Test
    fun emptyRegistryWritesEverything() {
        val plan = listOf(
            alarm("2026-09-28", 8, 0, 7, 15),
            alarm("2026-09-29", 10, 30, 9, 45)
        )
        val actions = MorningAlarmDiff.diff(plan, emptyMap())
        assertEquals(2, actions.size)
        assertTrue("空登记簿应全部为写入动作", actions.all { it is MorningAlarmDiff.Action.Write })
    }

    @Test
    fun unchangedPlanIsIdempotent() {
        val a = alarm("2026-09-28", 8, 0, 7, 15)
        val plan = listOf(a)
        val registry = mapOf("2026-09-28" to MorningAlarmPlan.label(a))

        val actions = MorningAlarmDiff.diff(plan, registry)
        assertTrue("计划未变时不应产生任何动作（幂等）", actions.isEmpty())
    }

    @Test
    fun neverRewritesRegisteredDateWhenDismissUnsupported() {
        // 核心契约：系统不支持删除时，即使标签变了也**不重写**。
        // 若重写，MIUI 会留下两条重复闹钟（实测），而旧的那条又删不掉。
        val original = alarm("2026-09-28", 8, 0, 7, 15)
        val changed = alarm("2026-09-28", 8, 30, 7, 45, name = "大学英语")
        val registry = mapOf("2026-09-28" to MorningAlarmPlan.label(original))

        val actions = MorningAlarmDiff.diff(listOf(changed), registry, allowDismiss = false)
        assertTrue("不支持删除时绝不重写已登记日期（否则重复堆积）", actions.isEmpty())
    }

    @Test
    fun rewritesWhenDismissSupported() {
        val original = alarm("2026-09-28", 8, 0, 7, 15)
        val changed = alarm("2026-09-28", 8, 30, 7, 45, name = "大学英语")
        val registry = mapOf("2026-09-28" to MorningAlarmPlan.label(original))

        val actions = MorningAlarmDiff.diff(listOf(changed), registry, allowDismiss = true)
        assertEquals("支持删除时应产出「先删后写」两个动作", 2, actions.size)
        assertTrue(actions[0] is MorningAlarmDiff.Action.Dismiss)
        assertTrue(actions[1] is MorningAlarmDiff.Action.Write)
        assertEquals(
            "删除必须用旧标签精确定位",
            MorningAlarmPlan.label(original),
            (actions[0] as MorningAlarmDiff.Action.Dismiss).label
        )
    }

    @Test
    fun staleDateIsNeverDismissedWhenUnsupported() {
        // 计划里已消失的日期：系统不支持删除时不得产出删除动作
        // （删不掉；产出动作会让调用方误以为清理成功）
        val stale = alarm("2026-09-20", 8, 0, 7, 15)
        val registry = mapOf("2026-09-20" to MorningAlarmPlan.label(stale))
        val newPlan = listOf(alarm("2026-09-28", 8, 0, 7, 15))

        val actions = MorningAlarmDiff.diff(newPlan, registry, allowDismiss = false)
        assertTrue(
            "不支持删除时不得产出删除动作，也不得重写新日期之外的条目",
            actions.all { it is MorningAlarmDiff.Action.Write }
        )
        assertEquals(1, actions.size)
    }

    @Test
    fun staleDateDismissedWhenSupported() {
        val stale = alarm("2026-09-20", 8, 0, 7, 15)
        val registry = mapOf("2026-09-20" to MorningAlarmPlan.label(stale))
        val newPlan = listOf(alarm("2026-09-28", 8, 0, 7, 15))

        val actions = MorningAlarmDiff.diff(newPlan, registry, allowDismiss = true)
        assertEquals(2, actions.size)
        assertTrue(actions.any { it is MorningAlarmDiff.Action.Dismiss })
        assertTrue(actions.any { it is MorningAlarmDiff.Action.Write })
    }

    @Test
    fun newDateAddedWhileOthersUnchanged() {
        val a = alarm("2026-09-28", 8, 0, 7, 15)
        val b = alarm("2026-09-29", 10, 30, 9, 45)
        val registry = mapOf("2026-09-28" to MorningAlarmPlan.label(a))

        val actions = MorningAlarmDiff.diff(listOf(a, b), registry)
        assertEquals("只有新增日期需要写入", 1, actions.size)
        val write = actions[0] as MorningAlarmDiff.Action.Write
        assertEquals("2026-09-29", write.alarm.courseDate.toString())
    }

    @Test
    fun labelChangesWithTimeAndName() {
        val a1 = alarm("2026-09-28", 8, 0, 7, 15, name = "高等数学")
        val a2 = alarm("2026-09-28", 8, 0, 7, 15, name = "大学英语")
        assertTrue(
            "课程名变化必须反映到标签，否则无法区分同名日期",
            MorningAlarmPlan.label(a1) != MorningAlarmPlan.label(a2)
        )
    }

    /**
     * 登记簿回收契约（2026-09-27 修复后语义）。
     *
     * 真机发现登记簿只增不减会坏在两点：
     *  1. **响过的条目必须丢弃**：系统时钟建的是一次性闹钟（`deleteAfterUse:true`），
     *     响过即被系统删除；登记簿若继续记着「已写入」，第二天早上就再也不会补位
     *     —— 症状是「只有第一条闹钟，之后每天早上都没有」；
     *  2. **超窗口的远过去条目必须丢弃**，否则登记簿无界增长（每天一行、一年 365 行）。
     *
     * 另外 v1 旧格式（`firedAt == null`）一律丢弃：它只记了「intent 已发出」，
     * 既无法判断是否已响，也可能根本没写成功（后台启动限制），留着只会把错误固化。
     */
    @Test
    fun registryPruneDropsFiredAndDistantPastButKeepsUpcoming() {
        val now = LocalDateTime.parse("2026-09-27T19:52:00")
        val pruned = MorningAlarmRegistry.prune(
            registry = mapOf(
                // 尚未到来 + 窗口内 → 保留
                "2026-09-28" to MorningAlarmRegistry.Entry(
                    firedAt = LocalDateTime.parse("2026-09-28T07:05:00"),
                    label = "早八·09-28 07:05 大学英语"
                ),
                // 今天早上已经响过（一次性闹钟已被系统删除）→ 丢弃，让下一条补位
                "2026-09-27" to MorningAlarmRegistry.Entry(
                    firedAt = LocalDateTime.parse("2026-09-27T07:05:00"),
                    label = "早八·09-27 07:05 大学英语"
                ),
                // 超出回收窗口的远过去 → 丢弃
                "2026-08-01" to MorningAlarmRegistry.Entry(
                    firedAt = LocalDateTime.parse("2026-08-01T07:05:00"),
                    label = "早八·08-01 07:05 高等数学"
                ),
                // v1 旧格式（无响铃时刻，不可信）→ 丢弃
                "2026-09-25" to MorningAlarmRegistry.Entry(firedAt = null, label = "早八·09-25 07:05 高等数学")
            ),
            now = now,
            retentionDays = MORNING_ALARM_RETENTION_DAYS
        )

        assertEquals("只应保留尚未到来且在窗口内的那条", setOf("2026-09-28"), pruned.keys)
    }

    @Test
    fun disabledRegistryKeepsEntriesToAvoidDuplicates() {
        // 关闭开关时不得清空登记簿：清空后重开开关会把这几天当「未登记」再写一次，
        // 而旧闹钟在系统时钟里删不掉 → 直接产生重复闹钟。
        val a = alarm("2026-09-28", 8, 0, 7, 15)
        val registry = mapOf("2026-09-28" to MorningAlarmPlan.label(a))

        // 重开开关后按同一计划同步：登记簿仍在 ⇒ 不产生任何写入动作（不重复）
        val actions = MorningAlarmDiff.diff(listOf(a), registry)
        assertTrue("登记簿保留时应幂等无动作（这正是防重复的关键）", actions.isEmpty())
    }
}
