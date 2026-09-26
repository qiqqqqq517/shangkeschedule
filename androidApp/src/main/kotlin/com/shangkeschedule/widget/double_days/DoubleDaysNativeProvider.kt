package com.shangkeschedule.widget.double_days

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 今日 / 明日双栏原生小组件接收器（渲染见 [DoubleDaysNativeRenderer]）。
 *
 * 后台排期（首个添加 / 最后一个移除）由基类 [ScheduledWidgetProvider] 统一处理（v3.69.5 去重）。
 */
class DoubleDaysNativeProvider : ScheduledWidgetProvider()
