package com.shangkeschedule.widget.next_course

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 「下一节课」小组件接收器（渲染见 [NextCourseNativeRenderer]）。
 *
 * 后台排期（首个添加 / 最后一个移除）由基类 [ScheduledWidgetProvider] 统一处理，
 * 本类刻意保持零逻辑：新增规格只需在这里声明，刷新链路（WorkManager 周期任务 +
 * 数据变更推送 + 尺寸变化重算）全部复用既有基类。
 */
class NextCourseNativeProvider : ScheduledWidgetProvider()
