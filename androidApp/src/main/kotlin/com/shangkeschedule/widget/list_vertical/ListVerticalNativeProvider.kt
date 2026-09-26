package com.shangkeschedule.widget.list_vertical

import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 垂直列表版小组件接收器（渲染见 [ListVerticalNativeRenderer]）。
 *
 * 后台排期（首个添加 / 最后一个移除）由基类 [ScheduledWidgetProvider] 统一处理（v3.69.5 去重）。
 */
class ListVerticalNativeProvider : ScheduledWidgetProvider()
