import com.shangkeschedule.tool.TimeTextUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 作息时间解析回归测试。
 *
 * 背景：WeeklyScheduleViewModel.calculateCurrentSectionIndex 原先直接对
 * split(":") 的结果调用 toInt()，遇到非数字时间串会抛 NumberFormatException；
 * 该函数在 combine 数据流与 60 秒轮询协程内被调用，抛出会导致整张周课表停止刷新。
 * 本测试锁定「任何非法输入都不抛异常、返回 null」这一契约。
 */
class TimeTextUtilsTest {

    @Test
    fun parsesValidTime() {
        assertEquals(0, TimeTextUtils.parseMinutesOfDayOrNull("00:00"))
        assertEquals(480, TimeTextUtils.parseMinutesOfDayOrNull("08:00"))
        assertEquals(485, TimeTextUtils.parseMinutesOfDayOrNull("8:5"))
        assertEquals(1439, TimeTextUtils.parseMinutesOfDayOrNull("23:59"))
        assertEquals(480, TimeTextUtils.parseMinutesOfDayOrNull(" 08:00 "))
    }

    @Test
    fun returnsNullInsteadOfThrowingOnMalformedInput() {
        // 以下每一条在旧实现中都会走到 toInt() 并抛 NumberFormatException
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("08:0a"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("aa:bb"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("1:2:3"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("08"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull(""))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("   "))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull(":"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull(null))
    }

    @Test
    fun rejectsOutOfRangeValues() {
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("24:00"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("08:60"))
        assertNull(TimeTextUtils.parseMinutesOfDayOrNull("-1:00"))
    }

    @Test
    fun isValidTimeOfDayMatchesParser() {
        assertTrue(TimeTextUtils.isValidTimeOfDay("08:00"))
        assertTrue(TimeTextUtils.isValidTimeOfDay("8:5"))
        assertFalse(TimeTextUtils.isValidTimeOfDay("08:0a"))
        assertFalse(TimeTextUtils.isValidTimeOfDay(""))
        assertFalse(TimeTextUtils.isValidTimeOfDay(null))
    }
}
