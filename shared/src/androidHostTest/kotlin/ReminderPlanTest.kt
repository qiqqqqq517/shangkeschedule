import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.notification.plan.ReminderPlan
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ReminderPlan] 纯决策回归测试。
 *
 * 这些断言补上了拆分前的空白：选择逻辑原先内联在 `CourseReminderScheduler.schedule()`，
 * 而该类持有 `Context` / `Intent` / `AlarmManager`，在只有 JUnit4 的单测环境里无法实例化，
 * 「过期丢弃 / 槽位耗尽 / occurrence 去重」三条分支此前**零覆盖**。
 */
class ReminderPlanTest {

    private fun course(
        id: String,
        date: String,
        start: String,
        end: String = "12:00",
        name: String = "高等数学"
    ) = WidgetCourse(
        id = id,
        name = name,
        teacher = "张老师",
        position = "A101",
        startTime = start,
        endTime = end,
        isSkipped = false,
        date = date,
        colorInt = 0
    )

    /** 顺序发码的假请求码分配器；记录分配历史以便断言「调用了几次」。 */
    private class FakeCodes(private val capacity: Int) {
        val allocated = mutableListOf<String>()
        private val byKey = mutableMapOf<String, Int>()

        fun codeOf(key: String): Int? {
            byKey[key]?.let { return it }
            if (allocated.size >= capacity) return null
            val code = 1000 + allocated.size
            allocated += key
            byKey[key] = code
            return code
        }
    }

    @Test
    fun reminderUsesLeadOffset() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "08:00")),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertEquals(1, entries.size)
        assertEquals(LocalDateTime(2026, 9, 28, 7, 40), entries[0].triggerAt, "提前 20 分钟即 07:40")
        assertEquals(1000, entries[0].code)
        assertEquals(codes.allocated.single(), entries[0].key, "key 必须与发码用的身份一致")
    }

    @Test
    fun pastReminderIsSkipped() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "08:00")),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 9, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertTrue(entries.isEmpty(), "提醒时刻已过（07:40 < 09:00）不得挂载")
        assertTrue(codes.allocated.isEmpty(), "被跳过的课不应消耗请求码")
    }

    @Test
    fun leadOffsetCrossingMidnightReportsPreviousDay() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "00:20", end = "01:50")),
            leadMinutes = 45,
            now = LocalDateTime(2026, 9, 27, 20, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertEquals(1, entries.size)
        assertEquals(
            LocalDateTime(2026, 9, 27, 23, 35),
            entries[0].triggerAt,
            "跨零点回绕必须落到前一天，否则会被误判成已过去"
        )
    }

    @Test
    fun crossingMidnightReminderIsDroppedOnceItsMomentPasses() {
        // 00:20 的课提前 45 分钟 → 23:35 于前一天；到了 23:50 它已经过去了
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "00:20", end = "01:50")),
            leadMinutes = 45,
            now = LocalDateTime(2026, 9, 27, 23, 50),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertTrue(entries.isEmpty(), "跨零点后的时刻同样受「已过期即丢弃」约束")
    }

    @Test
    fun unparsableDateIsSkippedWithoutStopping() {
        val codes = FakeCodes(capacity = 10)
        val good = course("c2", "2026-09-28", "08:00")
        val entries = ReminderPlan.select(
            courses = listOf(course("bad", "not-a-date", "08:00"), good),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertEquals(1, entries.size, "脏日期只跳过自己，不应中断后面的课")
        assertEquals(good.id, entries[0].course.id)
    }

    @Test
    fun unparsableStartTimeIsSkipped() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "")),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun slotLimitStopsSelection() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(
                course("c1", "2026-09-28", "08:00"),
                course("c2", "2026-09-28", "10:00"),
                course("c3", "2026-09-28", "14:00")
            ),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 1,
            codeOf = codes::codeOf
        )

        assertEquals(1, entries.size, "只剩 1 个槽位就只能挂 1 条")
        assertEquals(1, codes.allocated.size)
    }

    @Test
    fun nullCodeTerminatesSelection() {
        val codes = FakeCodes(capacity = 1)
        val entries = ReminderPlan.select(
            courses = listOf(
                course("c1", "2026-09-28", "08:00"),
                course("c2", "2026-09-28", "10:00")
            ),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 10, // 故意给足，逼 codeOf 自己返回 null
            codeOf = codes::codeOf
        )

        assertEquals(1, entries.size, "分配器返回 null 时必须终止本轮")
    }

    @Test
    fun zeroSlotsYieldNothingWithoutAllocating() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = listOf(course("c1", "2026-09-28", "08:00")),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 0,
            codeOf = codes::codeOf
        )

        assertTrue(entries.isEmpty())
        assertTrue(codes.allocated.isEmpty())
    }

    @Test
    fun sameOccurrenceConsumesSingleSlot() {
        val codes = FakeCodes(capacity = 1)
        val duplicate = course("c1", "2026-09-28", "08:00")
        val entries = ReminderPlan.select(
            courses = listOf(duplicate, duplicate),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 1,
            codeOf = codes::codeOf
        )

        assertEquals(2, entries.size)
        assertEquals(
            entries[0].code,
            entries[1].code,
            "同一次课只能占一个槽位，重复出现要复用同一个请求码"
        )
        assertEquals(1, codes.allocated.size)
    }

    @Test
    fun emptyCoursesYieldNothing() {
        val codes = FakeCodes(capacity = 10)
        val entries = ReminderPlan.select(
            courses = emptyList(),
            leadMinutes = 20,
            now = LocalDateTime(2026, 9, 28, 7, 0),
            availableSlots = 10,
            codeOf = codes::codeOf
        )

        assertTrue(entries.isEmpty())
    }
}
