package com.shangkeschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 跨天时先续排并派发完整同步，再尝试用已有快照及时重绘。 */
class WidgetDayBoundaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        if (intent?.action != WidgetDayBoundaryAlarmScheduler.ACTION_WIDGET_DAY_BOUNDARY) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runDayBoundaryActions(
                    hasWidget = WorkManagerHelper.hasAnyWidget(appContext),
                    scheduleNext = { WidgetDayBoundaryAlarmScheduler(appContext).schedule() },
                    scheduleRollover = { WorkManagerHelper.scheduleDailyRollover(appContext) },
                    enqueueSync = { WorkManagerHelper.enqueueDayBoundarySync(appContext) },
                    refresh = { updateAllWidgets(appContext, WidgetRefreshReason.REQUIRED) },
                    onFailure = { action, error -> Log.e(TAG, "跨天${action}失败", error) }
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetDayBoundary"
    }
}

/** 先保证自续与完整同步，快照重绘不等待 WorkManager，但只使用广播的有限预算。 */
internal suspend fun runDayBoundaryActions(
    hasWidget: Boolean,
    scheduleNext: () -> Unit,
    scheduleRollover: () -> Unit,
    enqueueSync: () -> Unit,
    refresh: suspend () -> Unit,
    onFailure: (String, Exception) -> Unit,
    refreshTimeoutMillis: Long = 6_000
) {
    if (!hasWidget) return

    fun attempt(label: String, action: () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(label, e)
        }
    }

    attempt("闹钟续排", scheduleNext)
    attempt("每日自愈续排", scheduleRollover)
    attempt("同步派发", enqueueSync)
    try {
        withTimeoutOrNull(refreshTimeoutMillis) { refresh() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure("快照重绘", e)
    }
}
