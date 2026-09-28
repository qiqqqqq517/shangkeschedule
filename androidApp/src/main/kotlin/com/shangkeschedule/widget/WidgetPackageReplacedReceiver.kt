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
                updateAllWidgets(context.applicationContext, WidgetRefreshReason.REQUIRED)
            } catch (e: Exception) {
                Log.e("WidgetPackageReplaced", "升级后刷新小组件失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
