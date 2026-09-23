package com.shangkeschedule.widget

import com.shangkeschedule.data.model.schedule_style.DualColorProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 小组件纯逻辑单测（v3.66.3 新增）。
 *
 * 覆盖两处已修复的缺陷，防止回归：
 * ① Tiny 组件的「剩余课数」把已跳过的课算了进去；
 * ② 四个 Renderer 的取色守卫只判上界不判负值，负索引会抛 IndexOutOfBoundsException
 *    （异常被 `updateAllWidgets` 的 try/catch 吞掉 → 组件静默停更）。
 */
class WidgetCourseSelectionTest {

    private val today = "2026-03-02"
    private val tomorrow = "2026-03-03"

    private fun course(
        id: String,
        start: String,
        end: String,
        date: String = today,
        skipped: Boolean = false,
        colorInt: Int = 0
    ) = WidgetCourseProto(
        id = id,
        name = "课程$id",
        position = "教一101",
        start_time = start,
        end_time = end,
        color_int = colorInt,
        is_skipped = skipped,
        teacher = "张老师",
        date = date
    )

    // ------------------------------------------------------------------ minutesOf

    @Test
    fun `minutesOf 解析标准与紧凑写法`() {
        assertEquals(510, WidgetCourseSelection.minutesOf("08:30") ?: -1)
        assertEquals(510, WidgetCourseSelection.minutesOf("8:30") ?: -1)
        assertEquals(510, WidgetCourseSelection.minutesOf("08:30:00") ?: -1)
        assertEquals(0, WidgetCourseSelection.minutesOf("00:00") ?: -1)
        assertEquals(1439, WidgetCourseSelection.minutesOf("23:59") ?: -1)
    }

    @Test
    fun `minutesOf 对非法输入返回 null`() {
        assertNull(WidgetCourseSelection.minutesOf(""))
        assertNull(WidgetCourseSelection.minutesOf("abc"))
        assertNull(WidgetCourseSelection.minutesOf("99:99"))
        assertNull(WidgetCourseSelection.minutesOf("08:70"))
        assertNull(WidgetCourseSelection.minutesOf("08"))
    }

    // ------------------------------------------------------------- 课程筛选与排序

    @Test
    fun `allToday 只取当日与未标注日期的课程并按开始时间升序`() {
        val courses = listOf(
            course("c", "14:00", "15:35"),
            course("a", "08:00", "09:35"),
            course("x", "10:00", "11:35", date = tomorrow),
            course("b", "10:00", "11:35", date = "")
        )
        assertEquals(listOf("a", "b", "c"), WidgetCourseSelection.allToday(courses, today).map { it.id })
    }

    @Test
    fun `remainingToday 排除已跳过与已结束的课程`() {
        val courses = listOf(
            course("ended", "08:00", "09:35"),
            course("ongoing", "10:00", "11:35"),
            course("skipped", "12:00", "13:35", skipped = true),
            course("later", "14:00", "15:35")
        )
        val ids = WidgetCourseSelection
            .remainingToday(courses, today, nowMinutes = 10 * 60)
            .map { it.id }
        // 正在进行的课（结束时间晚于 now）应保留
        assertEquals(listOf("ongoing", "later"), ids)
    }

    /**
     * 回归（P0-3）：Tiny 旧实现用 `todayAllCourses.size - indexOf(nextCourse)` 计算剩余课数，
     * 而该列表**包含已跳过的课**。本用例中 indexOf(c2)=1、size=4 → 旧实现显示 3，正确值为 2。
     */
    @Test
    fun `remainingToday 条数不把已跳过的课算进剩余数`() {
        val courses = listOf(
            course("c1", "08:00", "09:35"),
            course("c2", "10:00", "11:35"),
            course("c3", "12:00", "13:35", skipped = true),
            course("c4", "14:00", "15:35")
        )
        assertEquals(2, WidgetCourseSelection.remainingToday(courses, today, nowMinutes = 10 * 60).size)
    }

    @Test
    fun `remainingToday 对无法解析的结束时间按未结束处理`() {
        val courses = listOf(course("bad", "10:00", "oops"))
        val ids = WidgetCourseSelection
            .remainingToday(courses, today, nowMinutes = 23 * 60 + 59)
            .map { it.id }
        assertEquals(listOf("bad"), ids)
    }

    /**
     * 回归（P0-3）：Tiny 是四个 Renderer 中唯一未排序的，仅依赖上游 `CourseDao` 的
     * `ORDER BY day, startSection` 隐式顺序；自定义时间的课会被 SQL 排到所有节次课之后。
     */
    @Test
    fun `remainingToday 显式按开始时间排序，不依赖上游课程表顺序`() {
        val courses = listOf(
            course("late", "16:00", "17:35"),
            course("early", "10:00", "11:35")
        )
        val ids = WidgetCourseSelection
            .remainingToday(courses, today, nowMinutes = 0)
            .map { it.id }
        assertEquals(listOf("early", "late"), ids)
    }

    @Test
    fun `tomorrow 只取明日且排除已跳过`() {
        val courses = listOf(
            course("t1", "08:00", "09:35", date = tomorrow),
            course("t2", "10:00", "11:35", date = tomorrow, skipped = true),
            course("today", "12:00", "13:35")
        )
        assertEquals(listOf("t1"), WidgetCourseSelection.tomorrow(courses, tomorrow).map { it.id })
    }

    // ------------------------------------------------------------------ 取色安全

    private val palette = listOf(
        DualColorProto(light_color = 0xFF112233L, dark_color = 0xFF445566L),
        DualColorProto(light_color = 0xFF778899L, dark_color = 0xFFAABBCCL)
    )

    @Test
    fun `resolveCourseColor 命中合法索引`() {
        val pair = resolveCourseColor(palette, 1)
        assertEquals(0xFF778899L, pair?.light_color)
        assertEquals(0xFFAABBCCL, pair?.dark_color)
    }

    /**
     * 回归（P0-1）：旧守卫为 `colorInt < maps.size`，不判负值 —— `maps[-1]` 会抛
     * IndexOutOfBoundsException。此处必须返回 null（由调用方回落到兜底色），而非抛异常。
     */
    @Test
    fun `resolveCourseColor 对负索引返回 null 而不是抛异常`() {
        assertNull(resolveCourseColor(palette, -1))
        assertNull(resolveCourseColor(palette, Int.MIN_VALUE))
    }

    @Test
    fun `resolveCourseColor 对越界索引与空色池返回 null`() {
        assertNull(resolveCourseColor(palette, 2))
        assertNull(resolveCourseColor(palette, Int.MAX_VALUE))
        assertNull(resolveCourseColor(emptyList(), 0))
        assertNull(resolveCourseColor(null, 0))
    }
}
