import com.shangkeschedule.tool.HolidayRange
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [HolidayRange] 纯函数回归测试。
 *
 * 覆盖「手动设定节假日」新功能的边界：单日、跨月跨年、闰年二月、
 * 起止颠倒、以及误操作保护上限（[HolidayRange.MAX_DAYS]）。
 */
class HolidayRangeTest {

    @Test
    fun singleDayExpandsToItself() {
        assertEquals(listOf("2026-10-01"), HolidayRange.expand(LocalDate(2026, 10, 1), LocalDate(2026, 10, 1)))
    }

    /** 跨月：国庆假期 10-01..10-07。 */
    @Test
    fun rangeAcrossMonthsStaysInclusive() {
        val dates = HolidayRange.expand(LocalDate(2026, 10, 1), LocalDate(2026, 10, 7))
        assertEquals(7, dates?.size)
        assertEquals("2026-10-01", dates?.first())
        assertEquals("2026-10-07", dates?.last())
    }

    /** 跨年：寒假 2027-01-30..2027-02-03。 */
    @Test
    fun rangeAcrossYearsKeepsOrder() {
        val dates = HolidayRange.expand(LocalDate(2026, 12, 30), LocalDate(2027, 1, 2))
        assertEquals(listOf("2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02"), dates)
    }

    /** 闰年二月有 29 日；非闰年二月止于 28 日。 */
    @Test
    fun leapFebruaryIsExpandedCorrectly() {
        assertEquals(3, HolidayRange.expand(LocalDate(2028, 2, 28), LocalDate(2028, 3, 1))?.size)
        assertEquals(2, HolidayRange.expand(LocalDate(2027, 2, 28), LocalDate(2027, 3, 1))?.size)
    }

    /** 起止颠倒 → null（由调用方提示用户，而不是静默交换或写入空集）。 */
    @Test
    fun reversedRangeIsRejected() {
        assertNull(HolidayRange.expand(LocalDate(2026, 10, 7), LocalDate(2026, 10, 1)))
    }

    /** 上层保护：恰好 [HolidayRange.MAX_DAYS] 天（2026 平年全年 + 次年元旦）放行。 */
    @Test
    fun exactlyMaxDaysIsAllowed() {
        val dates = HolidayRange.expand(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1))
        assertEquals(HolidayRange.MAX_DAYS, dates?.size)
    }

    /** 超过上限一天即拒绝，避免误选跨世纪区间写入上万条日期。 */
    @Test
    fun overMaxDaysIsRejected() {
        assertNull(HolidayRange.expand(LocalDate(2026, 1, 1), LocalDate(2027, 1, 2)))
        assertNull(HolidayRange.expand(LocalDate(2000, 1, 1), LocalDate(2099, 1, 1)))
    }
}
