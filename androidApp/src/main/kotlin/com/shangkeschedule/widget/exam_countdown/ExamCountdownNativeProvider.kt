package com.shangkeschedule.widget.exam_countdown

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 「考试倒计时」小组件的 AppWidgetProvider（v4.67.0）。
 *
 * 与其余七个规格一致：Provider 只承担「被系统叫醒」的入口职责，
 * 更新/排期/尺寸变化全部由基类 [ScheduledWidgetProvider] 统一处理，
 * 渲染逻辑在 [ExamCountdownNativeRenderer]。
 */
class ExamCountdownNativeProvider : ScheduledWidgetProvider()
