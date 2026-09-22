package com.shangkeschedule.widget.double_days

import android.appwidget.AppWidgetManager
import android.content.Context
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.ScheduledWidgetProvider
import com.shangkeschedule.widget.updateAllWidgets

class DoubleDaysNativeProvider : ScheduledWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 启用更新计划任务表
        WorkManagerHelper.schedulePeriodicWork(context)

    }

    // 移除最后一个小组件时清除任务表
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManagerHelper.onWidgetDisabled(context)
    }
}
