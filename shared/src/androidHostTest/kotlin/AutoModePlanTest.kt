import com.shangkeschedule.notification.plan.AutoModePlan
import com.shangkeschedule.notification.plan.ReminderEngine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AutoModePlan] 纯决策回归测试。
 *
 * 补上拆分前的空白：过滤过期 + 槽位截断 + 编号原先内联在 `AutoModeScheduler.schedule()`，
 * 而该类持有 `Context` / `Intent` / `AlarmManager`，单测无法实例化。
 * 槽位号与 `AlarmScheduler.setAutoMode(intent, index, …)` 的编号规则必须一致，
 * 否则撤销时会漏掉闹钟——故此处显式锁定 0 基、连续。
 */
class AutoModePlanTest {

    private fun transition(date: String, hour: Int, minute: Int, enable: Boolean) =
        ReminderEngine.AutoModeTransition(
            date = LocalDate.parse(date),
            time = LocalTime(hour, minute),
            enable = enable
        )

    @Test
    fun pastTransitionsAreDropped() {
        val entries = AutoModePlan.select(
            transitions = listOf(
                transition("2026-09-27", 10, 0, enable = true),
                transition("2026-09-28", 8, 0, enable = true)
            ),
            now = LocalDateTime(2026, 9, 27, 12, 0),
            slotLimit = 60
        )

        assertEquals(1, entries.size, "09-27 10:00 已过去，只应留下 09-28 08:00")
        assertEquals(LocalDateTime(2026, 9, 28, 8, 0), entries[0].triggerAt)
        assertEquals(0, entries[0].index, "编号从 0 开始，且只在保留下来的序列上编号")
    }

    @Test
    fun enableFlagIsPreserved() {
        val entries = AutoModePlan.select(
            transitions = listOf(
                transition("2026-09-28", 8, 0, enable = true),
                transition("2026-09-28", 9, 40, enable = false)
            ),
            now = LocalDateTime(2026, 9, 28, 7, 0),
            slotLimit = 60
        )

        assertEquals(listOf(true, false), entries.map { it.enable })
        assertEquals(listOf(0, 1), entries.map { it.index })
    }

    @Test
    fun slotLimitTruncates() {
        val entries = AutoModePlan.select(
            transitions = listOf(
                transition("2026-09-28", 8, 0, enable = true),
                transition("2026-09-28", 9, 40, enable = false),
                transition("2026-09-28", 10, 0, enable = true)
            ),
            now = LocalDateTime(2026, 9, 28, 7, 0),
            slotLimit = 2
        )

        assertEquals(2, entries.size)
        assertEquals(listOf(0, 1), entries.map { it.index })
    }

    @Test
    fun zeroSlotLimitYieldsNothing() {
        val entries = AutoModePlan.select(
            transitions = listOf(transition("2026-09-28", 8, 0, enable = true)),
            now = LocalDateTime(2026, 9, 28, 7, 0),
            slotLimit = 0
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun emptyTransitionsYieldNothing() {
        val entries = AutoModePlan.select(
            transitions = emptyList(),
            now = LocalDateTime(2026, 9, 28, 7, 0),
            slotLimit = 60
        )

        assertTrue(entries.isEmpty())
    }
}
