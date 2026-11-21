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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.shangkeschedule.data.sync.WidgetDataSynchronizer
import kotlin.time.TimeSource

/**
 * 跨天锚点接收器：**先把快照滑到新的一天，再重绘**（v4.83.3）。
 *
 * ## 为什么同步必须就地做完，而不是只派发 Worker
 *
 * 组件渲染只消费 `widget` 库里的快照（`WidgetUpdateHelper.performUpdate` 读日期区间，
 * 不读主库）。快照窗口是「同步当天 → +6 天」，只有一次真正的同步才会把它滑到新的今天。
 * 而 v4.81.2 起同步被交给 `FullDataSyncWorker`（为的是不占用广播预算）——
 * 于是零点这次重绘**跑在同步之前**，第一帧用的仍是**昨天那份窗口**。
 *
 * 平时窗口还覆盖得到今天，看不出问题；但只要满足下面任一条，第一帧就是错的：
 * ① 设备 7 天以上没开过 App（窗口末端早已越过今天）；② 系统时间被前拨 / 换时区
 * （`TimeChangeReceiver` 只在本应用收到 TIME_SET 时才会同步，Doze 下不一定及时）；
 * ③ WorkManager 在夜间被 Doze / 厂商省电限流推迟（MIUI 上很常见）。
 * 此时零点后桌面会先渲染成「暂无课程 / 今日课程已结束」，直到 Worker 真正跑起来。
 *
 * 而「跨天锚点」这套机制存在的唯一理由就是**零点那次刷新必须是确定性的**；
 * 把数据面交给一个不保证及时的任务，等于把不确定性又引回来。故这里改为：
 * 自续链排好后**就地**做一次有界同步（默认 4s，占广播预算的小部分），
 * 成功则直接重绘（第一帧即新一天）；失败或超时才退回派发 Worker（保住原兜底语义）。
 *
 * 顺序不可调整：续链（自续锚点 + 每日自愈）必须在最前，否则慢机上预算耗光时
 * 明天就再也没有锚点了（v4.81.2 的事故）。
 */
class WidgetDayBoundaryReceiver : BroadcastReceiver(), KoinComponent {

    /** 快照同步数据源：零点先把窗口滑到新的一天，重绘才有意义（v4.83.3）。 */
    private val widgetDataSynchronizer: WidgetDataSynchronizer by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        if (intent?.action != WidgetDayBoundaryAlarmScheduler.ACTION_WIDGET_DAY_BOUNDARY) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val startedAt = TimeSource.Monotonic.markNow()
                runDayBoundaryActions(
                    hasWidget = WorkManagerHelper.hasAnyWidget(appContext),
                    scheduleNext = { WidgetDayBoundaryAlarmScheduler(appContext).schedule() },
                    scheduleRollover = { WorkManagerHelper.scheduleDailyRollover(appContext) },
                    syncData = { widgetDataSynchronizer.syncNow() },
                    enqueueSync = { WorkManagerHelper.enqueueDayBoundarySync(appContext) },
                    refresh = { updateAllWidgets(appContext, WidgetRefreshReason.REQUIRED) },
                    onFailure = { action, error -> Log.e(TAG, "跨天${action}失败", error) },
                    onSynced = { Log.d(TAG, "跨天快照已就地滑到新的一天（${startedAt.elapsedNow().inWholeMilliseconds} ms）") },
                    onSyncFallback = {
                        Log.w(TAG, "跨天快照就地同步未完成，改由 Worker 兜底（本轮重绘使用旧窗口）")
                    }
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

/**
 * 跨天动作序列（纯编排，无 Android 依赖，便于单测）。
 *
 * 顺序：自续锚点 → 每日自愈续排 → **就地同步快照** → （失败才）派发 Worker → 重绘。
 */
internal suspend fun runDayBoundaryActions(
    hasWidget: Boolean,
    scheduleNext: () -> Unit,
    scheduleRollover: () -> Unit,
    syncData: suspend () -> Unit,
    enqueueSync: () -> Unit,
    refresh: suspend () -> Unit,
    onFailure: (String, Exception) -> Unit,
    onSynced: () -> Unit = {},
    onSyncFallback: () -> Unit = {},
    syncTimeoutMillis: Long = 4_000,
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

    // ① 自续链先落地：慢机 / 预算耗光时，至少明天还有锚点（v4.81.2 事故的修复顺序）
    attempt("闹钟续排", scheduleNext)
    attempt("每日自愈续排", scheduleRollover)

    // ② 就地同步快照：组件只消费快照，同步没跑完就重绘 = 第一帧还是旧窗口
    val synced = try {
        withTimeoutOrNull(syncTimeoutMillis) { syncData() } != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure("快照同步", e)
        false
    }
    if (synced) {
        onSynced()
    } else {
        // 兜底：原语义（交给 Worker，它另有 10 分钟预算）
        onSyncFallback()
        attempt("同步兜底派发", enqueueSync)
    }

    // ③ 重绘：此时快照要么已滑到新一天，要么确实同步不了（保留旧快照也好过空白）
    try {
        withTimeoutOrNull(refreshTimeoutMillis) { refresh() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure("快照重绘", e)
    }
}
