package com.shangkeschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 小组件「跨天锚点」接收器（v4.80.4）：零点后把组件真正切到新的一天。
 *
 * 由 [WidgetDayBoundaryAlarmScheduler] 的精确闹钟拉起（每日
 * [com.shangkeschedule.notification.plan.WidgetRefreshEngine.DAY_BOUNDARY_TIME]）。
 *
 * ## 为什么必须「重新同步数据」而不只是「重绘」
 *
 * 重绘只读 [com.shangkeschedule.data.db.widget.WidgetDatabase] 里的既有快照，而快照的
 * 课程窗口是「同步日 → 同步日 + 6 天」（`WidgetDataSynchronizer.performSyncUnlocked`）。
 * 跨天后不重新同步，窗口不会向后滑：第 N+1 天时窗口末端仍是 N+6，
 * 「周课程」组件会比真实的一周少一天；跳过日（`skippedDates`）也停留在同步那一刻的口径。
 * 用户点开 App 数据却是对的 —— 这正是「跨天不同步」的观感来源。
 *
 * ## 为什么把重活交给 WorkManager，而不是在 `goAsync` 里做完（v4.81.2）
 *
 * 广播的 `goAsync()` 只给约 10 秒预算。上一版在本类里**顺序执行**「同步 + 渲染」：
 * 冷启动的 `syncNow()` 要打开双库、初始化 DataStore、跑多表查询，渲染侧还要过
 * 8 个 renderer 并带 3 次 300ms 节流 —— 慢机上极易超出窗口被系统回收。
 * 而被回收的代价不止「本次没刷成」：**自续链在 `finally` 里**，进程一旦被提前掐断，
 * 明天零点就没有闹钟了 —— 这会把「跨天不刷新」原样复现，而且比不修更难查
 * （只表现为隔天失效）。同一判断在 [com.shangkeschedule.service.BootEventReceiver]
 * 已有先例：开机全量同步同样刻意移交 WorkManager。
 *
 * 因此本类只做**廉价且必须立刻完成**的事，按这个顺序：
 *
 * 1. **先自续**：重挂明天的锚点 + 重锚每日零点自愈链。放在最前面，任何后续失败
 *    都不会让链条断掉。
 * 2. **再派发**：把「同步 + 重绘」交给 [FullDataSyncWorker]（Worker 有 10 分钟预算，
 *    不受广播窗口约束）。
 *
 * 组件是否翻页不再依赖本接收器是否存活：`FullDataSyncWorker` 完成同步后会主动重绘
 * （见该类实现），且失败还有 15 分钟 tick 兜底。
 *
 * ## 与既有触发链的关系（都是兜底，不重复）
 *
 * | 路径 | 时延 | 数据同步 |
 * | --- | --- | --- |
 * | 本接收器（精确闹钟） | 零点后 20 秒 | 是（经 Worker） |
 * | `DailyRolloverWorker`（WorkManager 00:05） | Doze 下可推迟数小时 | 是 |
 * | `WidgetUiUpdateWorker`（15 分钟 tick） | 不保证 | 否（只重绘） |
 */
class WidgetDayBoundaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        if (intent?.action != WidgetDayBoundaryAlarmScheduler.ACTION_WIDGET_DAY_BOUNDARY) return

        val appContext = ctx.applicationContext
        Log.d(TAG, "跨天锚点触发：重挂明日锚点并派发同步")

        // ① 自续（最优先）：AlarmManager 的一次性闹钟不会自动重排，断了就永远不再响。
        //    桌面已无组件时不再自续 —— `onWidgetDisabled` 会撤掉本闹钟，但「移除发生在
        //    本次触发之前」这类竞态由这里兜住，不留孤儿闹钟。
        val hasWidget = runCatching { WorkManagerHelper.hasAnyWidget(appContext) }.getOrDefault(false)
        if (hasWidget) {
            runCatching { WidgetDayBoundaryAlarmScheduler(appContext).schedule() }
                .onFailure { Log.e(TAG, "跨天锚点自续失败", it) }
            // 重锚 WorkManager 的每日自愈链：它可能因 WorkManager 清理而缺失。
            runCatching { WorkManagerHelper.scheduleDailyRollover(appContext) }
                .onFailure { Log.e(TAG, "重锚每日零点自愈失败", it) }
        } else {
            Log.d(TAG, "桌面已无小组件，跨天锚点不再自续")
        }

        // ② 派发重活：同步 + 重绘放进 Worker，避免撑爆 goAsync 的 ~10s 预算（见类 KDoc）。
        //    即使此处被系统回收，① 已执行完，明天的锚点仍在。
        runCatching { WorkManagerHelper.enqueueDayBoundarySync(appContext) }
            .onFailure { Log.e(TAG, "跨天同步任务派发失败", it) }
    }

    private companion object {
        const val TAG = "WidgetDayBoundary"
    }
}
