import com.shangkeschedule.data.logic.CoupleFreeTimeCalculator
import com.shangkeschedule.data.logic.MinuteRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D2「找共同空闲」（v4.66.0）的纯逻辑单测。
 *
 * 覆盖的是最容易出错的三件事：
 * 1. **首尾相接不算空档**（第 2 节 08:55 结束、第 3 节 08:55 开始 ⇒ 中间没有可用时间）；
 * 2. **占用先裁到作息窗口内**（07:00 的早课不该把窗口外的空档算丢/算错）；
 * 3. **碎片过滤**（课间 10 分钟不输出，但合并后 ≥ 30 分钟的连续空档要输出）。
 *
 * 真机只负责验证「界面把数据喂对了」（周次、两张表的合并），算法分支都在这里锁死。
 */
class CoupleFreeTimeCalculatorTest {

    private val calc = CoupleFreeTimeCalculator

    // --- parseToMinutes ---

    @Test
    fun parseToMinutes_reads_zero_padded_and_loose_forms() {
        assertEquals(8 * 60, calc.parseToMinutes("08:00"))
        assertEquals(8 * 60 + 5, calc.parseToMinutes("8:05"))
        assertEquals(23 * 60 + 59, calc.parseToMinutes("23:59"))
        assertEquals(0, calc.parseToMinutes("00:00"))
    }

    @Test
    fun parseToMinutes_rejects_garbage() {
        assertEquals(null, calc.parseToMinutes(null))
        assertEquals(null, calc.parseToMinutes(""))
        assertEquals(null, calc.parseToMinutes("   "))
        assertEquals(null, calc.parseToMinutes("08"))
        assertEquals(null, calc.parseToMinutes("08:00:00"))
        assertEquals(null, calc.parseToMinutes("24:00"))
        assertEquals(null, calc.parseToMinutes("08:60"))
        assertEquals(null, calc.parseToMinutes("aa:bb"))
    }

    // --- mergeRanges ---

    @Test
    fun mergeRanges_joins_overlapping_and_touching_ranges() {
        // 08:00–08:55 与 08:55–09:50 首尾相接 ⇒ 合并成一段（中间没有空档）
        val merged = calc.mergeRanges(
            listOf(
                MinuteRange(8 * 60, 8 * 60 + 55),
                MinuteRange(8 * 60 + 55, 9 * 60 + 50)
            )
        )
        assertEquals(1, merged.size)
        assertEquals(MinuteRange(8 * 60, 9 * 60 + 50), merged[0])
    }

    @Test
    fun mergeRanges_keeps_disjoint_ranges_separate_and_sorted() {
        val merged = calc.mergeRanges(
            listOf(
                MinuteRange(14 * 60, 15 * 60),
                MinuteRange(8 * 60, 9 * 60)
            )
        )
        assertEquals(listOf(MinuteRange(8 * 60, 9 * 60), MinuteRange(14 * 60, 15 * 60)), merged)
    }

    @Test
    fun mergeRanges_drops_empty_ranges() {
        assertEquals(emptyList(), calc.mergeRanges(listOf(MinuteRange(10 * 60, 10 * 60))))
        assertEquals(emptyList(), calc.mergeRanges(emptyList()))
    }

    // --- freeBlocksForDay ---

    @Test
    fun freeBlocksForDay_returns_gaps_at_least_thirty_minutes() {
        // 窗口 08:00–12:00，占用 08:55–10:00 ⇒ 空档 08:00–08:55（55 分钟）与 10:00–12:00（120 分钟）
        val blocks = calc.freeBlocksForDay(
            day = 1,
            busyRanges = listOf(MinuteRange(8 * 60 + 55, 10 * 60)),
            windowStartMinutes = 8 * 60,
            windowEndMinutes = 12 * 60
        )
        assertEquals(2, blocks.size)
        assertEquals(8 * 60, blocks[0].startMinutes)
        assertEquals(8 * 60 + 55, blocks[0].endMinutes)
        assertEquals(55, blocks[0].durationMinutes)
        assertEquals(10 * 60, blocks[1].startMinutes)
        assertEquals(12 * 60, blocks[1].endMinutes)
    }

    @Test
    fun freeBlocksForDay_skips_short_gaps() {
        // 窗口 09:00–10:00，占用 09:20–09:40 ⇒ 两侧各 20 分钟，都不够 30 分钟
        val blocks = calc.freeBlocksForDay(
            day = 1,
            busyRanges = listOf(MinuteRange(9 * 60 + 20, 9 * 60 + 40)),
            windowStartMinutes = 9 * 60,
            windowEndMinutes = 10 * 60
        )
        assertEquals(emptyList(), blocks)
    }

    @Test
    fun freeBlocksForDay_clips_busy_ranges_outside_window() {
        // 07:00 的早课在窗口（08:00–12:00）之外：既不该吃掉 08:00 起点的空档，也不该产生负区间
        val blocks = calc.freeBlocksForDay(
            day = 1,
            busyRanges = listOf(
                MinuteRange(7 * 60, 7 * 60 + 45),
                MinuteRange(10 * 60, 12 * 60 + 30)
            ),
            windowStartMinutes = 8 * 60,
            windowEndMinutes = 12 * 60
        )
        assertEquals(1, blocks.size)
        assertEquals(8 * 60, blocks[0].startMinutes)
        assertEquals(10 * 60, blocks[0].endMinutes)
    }

    @Test
    fun freeBlocksForDay_returns_whole_window_when_no_busy() {
        val blocks = calc.freeBlocksForDay(
            day = 3,
            busyRanges = emptyList(),
            windowStartMinutes = 8 * 60,
            windowEndMinutes = 18 * 60
        )
        assertEquals(1, blocks.size)
        assertEquals(3, blocks[0].day)
        assertEquals(600, blocks[0].durationMinutes)
    }

    @Test
    fun freeBlocksForDay_returns_nothing_for_inverted_window() {
        assertEquals(
            emptyList(),
            calc.freeBlocksForDay(
                day = 1,
                busyRanges = emptyList(),
                windowStartMinutes = 18 * 60,
                windowEndMinutes = 8 * 60
            )
        )
    }

    // --- compute ---

    @Test
    fun compute_merges_both_tables_and_sorts_by_day() {
        // 本人：周一 08:00–09:40；对方：周一 09:40–11:30、周三 14:00–15:00
        // 合并后周一被占 08:00–11:30 ⇒ 只剩 11:30–18:00；周三两侧各一段
        val busy = mapOf(
            1 to listOf(
                MinuteRange(8 * 60, 9 * 60 + 40),
                MinuteRange(9 * 60 + 40, 11 * 60 + 30)
            ),
            3 to listOf(MinuteRange(14 * 60, 15 * 60))
        )
        val blocks = calc.compute(
            days = listOf(1, 3),
            busyByDay = busy,
            windowStartMinutes = 8 * 60,
            windowEndMinutes = 18 * 60
        )
        assertEquals(listOf(1, 3, 3), blocks.map { it.day })
        assertEquals(11 * 60 + 30, blocks[0].startMinutes)
        assertEquals(18 * 60, blocks[0].endMinutes)
        assertEquals(390, blocks[0].durationMinutes)
        assertEquals(8 * 60, blocks[1].startMinutes)
        assertEquals(14 * 60, blocks[1].endMinutes)
        assertEquals(15 * 60, blocks[2].startMinutes)
        assertEquals(18 * 60, blocks[2].endMinutes)
    }

    @Test
    fun compute_skips_days_without_busy_data_and_respects_custom_min_duration() {
        val blocks = calc.compute(
            days = listOf(1, 2),
            busyByDay = mapOf(2 to listOf(MinuteRange(9 * 60, 10 * 60))),
            windowStartMinutes = 8 * 60,
            windowEndMinutes = 12 * 60,
            // 提高门槛到 3 小时：周一 4 小时整段保留，周二两侧各 1 小时被过滤
            minDurationMinutes = 180
        )
        assertEquals(listOf(1), blocks.map { it.day })
        assertEquals(240, blocks[0].durationMinutes)
    }

    @Test
    fun compute_returns_empty_when_window_too_small() {
        assertTrue(
            calc.compute(
                days = listOf(1),
                busyByDay = emptyMap(),
                windowStartMinutes = 8 * 60,
                windowEndMinutes = 8 * 60 + 20
            ).isEmpty()
        )
    }

    // --- formatMinutes ---

    @Test
    fun formatMinutes_pads_to_hh_mm() {
        assertEquals("08:00", calc.formatMinutes(8 * 60))
        assertEquals("09:05", calc.formatMinutes(9 * 60 + 5))
        assertEquals("00:00", calc.formatMinutes(0))
        assertEquals("23:59", calc.formatMinutes(23 * 60 + 59))
        // 越界值收敛到合法区间，避免复制文本里出现 "25:00"
        assertEquals("23:00", calc.formatMinutes(25 * 60))
        assertEquals("00:00", calc.formatMinutes(-10))
    }
}
