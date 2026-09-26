package com.shangkeschedule.widget.compact

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 今日课程（紧凑版）原生小组件接收器（渲染见 [CompactNativeRenderer]）。
 *
 * 后台排期（首个添加 / 最后一个移除）由基类 [ScheduledWidgetProvider] 统一处理（v3.69.5 去重）。
 */
class CompactNativeProvider : ScheduledWidgetProvider()
