package com.shangkeschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用升级广播接收器。
 *
 * 背景：小组件的周期刷新完全交给 WorkManager（`updatePeriodMillis = 0`，
 * 系统侧从不主动推）。用户升级 App 后、打开 App 前，桌面停留的是旧版渲染
 * （旧配色 + 旧取色逻辑）；省电策略严格的机器上 15 分钟 tick 还可能被推迟，
 * 用户会长时间看到「升级了但小组件还是老样子」，误报为修复残留或缓存问题。
 * 打开一次 App 即刷新（SyncManager 冷启动即推），但「升级即刷新」此前缺失。
 *
 * 工作原理：收到 MY_PACKAGE_REPLACED（仅发给自身，需静态注册）→ goAsync
 * 后台推一次全量渲染（REQUIRED，不可合并丢弃）。不在此处读 DataStore 或
 * 做重活：渲染链自带超时保留旧快照语义，失败由下一次 tick 兜底。
 */
class WidgetPackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Log.d("WidgetPackageReplaced", "应用已升级，立即刷新全部小组件...")

                // 升级会清除本应用的 AlarmManager 闹钟（系统按「强制停止」语义处理更新），
                // 跨天锚点因此不会自己回来 —— 重挂一次，否则升级后首次跨天就不翻页了
                // （v4.81.2）。与 onEnabled 一样只在桌面确有组件时挂。
                runCatching {
                    if (WorkManagerHelper.hasAnyWidget(context.applicationContext)) {
                        WidgetDayBoundaryAlarmScheduler(context.applicationContext).schedule()
                    }
                }.onFailure { Log.e("WidgetPackageReplaced", "升级后重挂跨天锚点失败", it) }

                updateAllWidgets(context.applicationContext, WidgetRefreshReason.REQUIRED)
            } catch (e: Exception) {
                Log.e("WidgetPackageReplaced", "升级后刷新小组件失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
