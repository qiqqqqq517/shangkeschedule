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

    /**
     * 渲染链路移出主线程（v3.66.3）。
     *
     * 原实现为 `Dispatchers.Main.immediate`，使 DB 读取、4 组 RemoteViews 构造以及组间
     * 3 次 300ms 节流全部压在主线程上；而 `goAsync()` 只提供约 10s 预算，主线程一旦被阻塞
     * 就会直接拖慢刷新甚至被系统掐断。渲染本身不触碰任何 View（只构造 RemoteViews 描述对象），
     * 因此放在 Default 上是安全的。
     */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 系统会给 4 个 receiver 同时各发一次 onUpdate；标为 SYSTEM_NUDGE 以便与正在进行的
        // 渲染合并，消除同一份数据的重复渲染（v3.66.3）。
        refreshInBackground(context, WidgetRefreshReason.SYSTEM_NUDGE)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 尺寸变化会改变「可容纳条数」，必须真正重算，不能当作可丢弃的系统提醒（v3.66.3）
        refreshInBackground(context, WidgetRefreshReason.REQUIRED)
    }

    private fun refreshInBackground(context: Context, reason: WidgetRefreshReason) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateAllWidgets(context, reason)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
