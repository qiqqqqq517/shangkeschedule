package com.shangkeschedule.ui.theme

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.DualColor

/**
 * 利落（TIMETABLE）/课表体系专用固定色。
 *
 * 这些是预设主题的功能色：不随动态取色/用户主题变化，与语义 token 分开管理，
 * 避免在各界面散落重复的 Color(0x…) 字面量。
 */
object TimetableDefaults {
    /** 无颜色池可用时的兜底课程色（青色系）。 */
    val fallbackCourseColor = DualColor(light = Color(0xFFE0F7FA), dark = Color(0xFF006064))

    /** 今日待办课程条用色（青色系，与待办语义绑定）。 */
    val todoCourseColor = DualColor(light = Color(0xFFB2EBF2), dark = Color(0xFF0097A7))
}
