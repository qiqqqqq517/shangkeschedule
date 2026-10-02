package com.shangkeschedule.widget.week_courses

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 「周课程」小组件的 AppWidgetProvider（v4.67.0，C3）。
 *
 * 与其余五个规格一致：Provider 只承担「被系统叫醒」的入口职责，
 * 更新/排期/尺寸变化全部由基类 [ScheduledWidgetProvider] 统一处理，
 * 渲染逻辑在 [WeekCoursesNativeRenderer]。
 */
class WeekCoursesNativeProvider : ScheduledWidgetProvider()
