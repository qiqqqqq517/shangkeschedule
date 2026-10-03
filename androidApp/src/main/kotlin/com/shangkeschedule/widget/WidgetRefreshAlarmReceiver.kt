package com.shangkeschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

/**
 * 小组件「课表边界」精确闹钟接收器（v4.67.36）。
 *
 * ## 它解决什么
 *
 * 小组件刷新此前**只有一条固定 15 分钟 tick**（`WidgetUiUpdateWorker`）。
 * tick 是兜底，不是主路径：用户在 10:14 下课，而 tick 恰好在 10:12 跑过，
 * 那么组件要到 10:27 才把「正在上课」翻成「下一节课」——
 * 而小组件正是「不打开 App 就能看到课表」的地方，这 13 分钟的错误**用户一眼就看见**。
 *
 * 竞品「星链课表」的做法（`WidgetRefreshScheduler.addWeekTodayBoundaries`）：
 * 不做固定 tick，而是按课表反解出每节课的 4 个状态翻转点，
 * 用精确闹钟在那一刻直接刷新。本类是该思路的落地。
 *
 * ## 与 tick 的关系（务必保留 tick）
 *
 * 精确闹钟**覆盖不到**全部情形，tick 仍然是必需的：
 * ① 精确闹钟权限被撤销时（降级为不精确，可能延迟很久）；
 * ② 当天完全没课时（「下一节课」指向明天的场景，需要一个周期性归零）；
 * ③ 课表在**明天**之后才变化，而今天的排程已定（跨天由
 * [com.shangkeschedule.service.DailyRolloverWorker] 自愈重排）。
 * 两者是「主路径 + 兜底」，删掉任何一个都会让组件出现某些时刻不刷新。
 */
class WidgetRefreshAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        // 精确闹钟触发的唯一目的就是「现在刷新一次」；action 不带参数，
        // 因为排程端已把刷新点去重（见 WidgetRefreshEngine.refreshPoints）。
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 理由必须是 REQUIRED：这是「时间推进导致的重绘」，
                // 与数据变更同级，绝不能被 SYSTEM_NUDGE 的合并规则吞掉。
                updateAllWidgets(ctx, WidgetRefreshReason.REQUIRED)
            } catch (e: Exception) {
                Log.e(TAG, "课表边界刷新小组件失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WidgetRefreshAlarm"

        /** 本接收器的专属 action（与其他闹钟子系统的 action 完全隔离）。 */
        const val ACTION_WIDGET_REFRESH =
            "com.shangkeschedule.action.WIDGET_BOUNDARY_REFRESH"
    }
}