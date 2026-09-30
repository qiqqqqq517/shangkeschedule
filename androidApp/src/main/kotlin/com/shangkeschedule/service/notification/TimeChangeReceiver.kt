package com.shangkeschedule.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.service.notification.schedule.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 系统时间/时区变更监听：**闹钟时间基准的纠偏入口**。
 *
 * ## 为什么必须有
 *
 * [AlarmScheduler] 排程时把「日期 + 时刻」用 `TimeZone.currentSystemDefault()` 一次性
 * 固化成 epoch 毫秒交给 AlarmManager。此后：
 *
 * - 用户换时区（出差 / 跨时区使用）→ 已排闹钟仍按旧时区触发，整体偏移；
 * - 用户改系统时间 / NTP 校时跳变 → 同样失准。
 *
 * 本项目此前对 `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_DATE_CHANGED`
 * **零监听**（全仓搜索无命中），只能等下一次开机、零点自愈或用户打开 App 才纠正 ——
 * 闹钟最多偏掉一整天。
 *
 * ## 覆盖的动作
 *
 * | action | 触发场景 |
 * |---|---|
 * | `ACTION_TIMEZONE_CHANGED` | 换时区 |
 * | `ACTION_TIME_CHANGED` | 改系统时间 / NTP 校时 |
 * | `ACTION_DATE_CHANGED` | 跨日（`TIME_SET` 不含日期变更时的补漏） |
 *
 * ## 收尾
 *
 * 这三个广播都**不能**静态注册到 manifest（`ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED`
 * 从 v3 起是 manifest-only 之外的隐式广播，静默安装受限），故按项目既有做法在
 * [MyApplication] 启动期运行时注册，与进程同寿、无需反注册。
 */
class TimeChangeReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private val widgetRepository: WidgetRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        // 运行时注册的接收器在同一进程内，无需 goAsync；排程是 IO + 闹钟挂载，放后台协程。
        Log.d(TAG, "收到 ${intent.action}，立即重排闹钟...")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val summary = NotificationScheduler(
                    context = appContext,
                    appSettingsRepository = appSettingsRepository,
                    widgetRepository = widgetRepository
                ).reschedule()
                Log.d(
                    TAG,
                    "时间/时区变更后重排完成：提醒 ${summary.reminderCount} 条、" +
                        "自动模式 ${summary.autoModeCount} 条、早八 ${summary.morningAlarmResult}"
                )
            } catch (e: Exception) {
                // 读库未就绪等一律不抛给系统：闹钟旧值仍在，用户感知不到本次纠偏失败，
                // 下次开机/零点自愈/改设置都会再排一轮。
                Log.e(TAG, "时间/时区变更后重排失败，保留既有排程", e)
            }
        }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
    }
}

/** Application 启动期调用一次（进程生命周期内有效，与进程同寿，无需反注册）。 */
fun registerTimeChangeWatcher(context: Context) {
    runCatching {
        ContextCompat.registerReceiver(
            context,
            TimeChangeReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }.onFailure { Log.e("TimeChangeWatcher", "注册时间/时区变更监听失败", it) }
}

/** 本接收器处理的动作集合（供 onReceive 快速过滤，也便于单测锁定）。 */
internal val HANDLED_ACTIONS = setOf(
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_DATE_CHANGED
)
