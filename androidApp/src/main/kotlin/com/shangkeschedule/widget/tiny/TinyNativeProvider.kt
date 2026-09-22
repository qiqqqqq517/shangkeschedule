package com.shangkeschedule.widget.tiny

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.ScheduledWidgetProvider
import com.shangkeschedule.widget.updateAllWidgets

class TinyNativeProvider : ScheduledWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkManagerHelper.schedulePeriodicWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManagerHelper.onWidgetDisabled(context)
    }
}
