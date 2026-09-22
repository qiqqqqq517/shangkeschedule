package com.shangkeschedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class ScheduledWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshInBackground(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refreshInBackground(context)
    }

    private fun refreshInBackground(context: Context) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
