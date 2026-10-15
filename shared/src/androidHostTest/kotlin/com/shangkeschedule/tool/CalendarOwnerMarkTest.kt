package com.shangkeschedule.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CalendarOwnerMark]：系统日历同步的归属判定。
 *
 * 锁住一条**数据安全**契约：
 * 用户在「上课」日历里**手工添加**的事件（生日 / 社团 / 临时提醒），
 * 绝不能被课表同步删除或改写。
 *
 * 回归背景：删除分支原先判据是「开始时刻不在期望集合里就删」，
 * 那问的是「这一刻有没有课」而非「这条事件是谁写的」，
 * 于是手工事件会在下一次同步被静默删除 —— 用户无从察觉、也无处追责。
 */
class CalendarOwnerMarkTest {

    @Test
    fun stampedEventIsOwned() {
        val marked = CalendarOwnerMark.stamp("教师：王海柳")
        assertTrue(CalendarOwnerMark.isOwnedBy(marked))
    }

    @Test
    fun manualEventIsNotOwned() {
        assertFalse("用户手工事件绝不能被判为本应用所有",
            CalendarOwnerMark.isOwnedBy("生日聚会"))
        assertFalse(CalendarOwnerMark.isOwnedBy(""))
        assertFalse(CalendarOwnerMark.isOwnedBy(null))
    }

    /**
     * 关键回归：**无教师信息的课也必须打标记**。
     *
     * 漏掉这一条的后果是：没有教师的课写出去时描述为空 → 读回时判为「非本应用所有」
     * → 课被删课删除（或反过来，删不掉）→ 每次同步都反复增删同一条事件。
     */
    @Test
    fun emptyBodyStillCarriesOwnership() {
        val marked = CalendarOwnerMark.stamp("")
        assertTrue("无教师也必须留下归属标记", CalendarOwnerMark.isOwnedBy(marked))
        assertEquals("", CalendarOwnerMark.unmark(marked))
    }

    @Test
    fun unmarkRestoresBody() {
        assertEquals("教师：王海柳", CalendarOwnerMark.unmark(CalendarOwnerMark.stamp("教师：王海柳")))
    }

    @Test
    fun unmarkReturnsNullForForeignEvent() {
        assertNull(CalendarOwnerMark.unmark("生日聚会"))
        assertNull(CalendarOwnerMark.unmark(null))
    }

    /** 标记必须在**开头**：用户若把它删到行尾，归属就丢了。 */
    @Test
    fun markIsAlwaysAtTheBeginning() {
        assertTrue(CalendarOwnerMark.stamp("x").startsWith(CalendarOwnerMark.OWNER_MARK_PREFIX))
    }

    /**
     * 标记必须与正文互不吞并：正文里含 `]` 时仍能正确还原。
     */
    @Test
    fun bodyContainingBracketsRoundTrips() {
        val body = "备注：教室 [A栋] 302"
        assertEquals(body, CalendarOwnerMark.unmark(CalendarOwnerMark.stamp(body)))
    }

    /** 前缀判定必须是「严格的 startsWith」，不能是 contains —— 否则正文里提到标记也会被误判。 */
    @Test
    fun ownershipRequiresPrefixNotContainment() {
        assertFalse(
            "正文里恰好含有标记文本，不等于这条事件由本应用写入",
            CalendarOwnerMark.isOwnedBy("备注：本条来自[上课:]其它应用")
        )
    }
}