package com.shangkeschedule.widget.list_vertical

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.ScheduledWidgetProvider
import com.shangkeschedule.widget.updateAllWidgets

/**
 * 垂直列表版小组件接收器
 * 负责响应系统刷新广播并触发 ListVerticalNativeRenderer 进行渲染
 */
class ListVerticalNativeProvider : ScheduledWidgetProvider() {
    /**
     * 当第一个该类型的小组件添加到桌面时调用
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 确保后台任务正在运行
        WorkManagerHelper.schedulePeriodicWork(context)
    }

    /**
     * 当该类型的最后一个小组件从桌面移除时调用
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManagerHelper.onWidgetDisabled(context)
    }
}
