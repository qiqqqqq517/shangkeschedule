package com.shangkeschedule.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.data.sync.WidgetDataSynchronizer
import com.shangkeschedule.service.notification.schedule.NotificationSyncWorker
import com.shangkeschedule.service.notification.schedule.NotificationScheduler
import com.shangkeschedule.widget.WidgetDayBoundaryAlarmScheduler
import com.shangkeschedule.widget.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 系统时间/时区/语言变更监听：**闹钟时间基准的纠偏入口**。
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
 * | `ACTION_TIME_CHANGED`（`android.intent.action.TIME_SET`） | 改系统时间 / NTP 校时 |
 * | `ACTION_DATE_CHANGED` | 跨日（`TIME_SET` 不含日期变更时的补漏） |
 * | `ACTION_LOCALE_CHANGED` | 系统语言切换 —— 影响星期/日期的本地化渲染 |
 *
 * ## `DATE_CHANGED` 投递不到本接收器（v4.80.4 更正）
 *
 * 上表中的 `ACTION_DATE_CHANGED` **不能当作「零点」的触发点**：它不在官方隐式广播豁免清单
 * 内（媒体挂载、`TIME_SET`、`TIMEZONE_CHANGED`、`LOCALE_CHANGED` 等在清单里，它不在），
 * 且 AOSP 发送方也没有加 `FLAG_RECEIVER_INCLUDE_BACKGROUND`
 * （android10-release `AlarmManagerService`：`new Intent(ACTION_DATE_CHANGED)` +
 * `FLAG_RECEIVER_REPLACE_PENDING | FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS`）。
 * targetSdk ≥ 26 时 manifest 接收器收不到它 —— 这里保留该 action 只是「万一某 OEM 投递」
 * 的免费机会，**跨天另由 `WidgetDayBoundaryReceiver` 的精确闹钟负责**（每日 00:00:20）。
 *
 * ## 为什么要顺带同步小组件数据（v4.80.4）
 *
 * 本接收器原先只调 [NotificationScheduler.reschedule]——它会重绘组件，但**不重建组件快照**。
 * 用户把系统日期往前拨（或换时区导致本地日期变化）时，快照窗口仍以旧「今天」为锚，
 * 组件会继续显示旧日期区间的内容（含已经过去的日程 / 考试）。故这里先
 * [WidgetDataSynchronizer.syncNow] 把窗口滑到新的今天，再重排闹钟并重绘。
 *
 * ## 注册方式：静态注册到 manifest（XL-014）
 *
 * 此前本类由 [MyApplication] 在启动期**运行时注册**，并在上版 KDoc 里断言
 * 「这三个广播不能静态注册到 manifest」。该断言不成立，已按下列依据改为静态注册：
 *
 * 1. 以上四个 action 全部是**受保护的系统广播**（第三方应用无法发送），且
 *    `TIME_SET` / `TIMEZONE_CHANGED` / `LOCALE_CHANGED` 都在官方隐式广播豁免清单内，
 *    manifest 静态声明照常投递。
 *
 *    更正（v4.80.4）：旧版本这里的依据写的是「受保护系统广播不受隐式广播限制」——
 *    该推理**不成立**。限制按 action 是否在豁免清单 / 发送方是否带
 *    `FLAG_RECEIVER_INCLUDE_BACKGROUND` 判定，与「是否受保护」无关；
 *    `DATE_CHANGED` 正是因此收不到（见上文「`DATE_CHANGED` 投递不到本接收器」）。
 *    结论不变的部分：静态注册本身是对的，只是不能再把 `DATE_CHANGED` 计入覆盖范围。
 * 2. 实证：星链课表（targetSdk 36）的 manifest 正是把这四个 action
 *    静态声明在其 8 个小组件 receiver 上，与本项目的目标配置一致。
 * 3. 运行时注册的根本缺陷：与进程同寿。用户在 App 未启动时改时区/改系统时间，
 *    已排闹钟不重排 → 课表整体偏移且 App 无感知。
 *
 * 静态注册后由系统直接拉起进程，不依赖用户是否打开过 App。
 */
class TimeChangeReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private val widgetRepository: WidgetRepository by inject()

    /** 组件快照的数据源：时间/时区/日期变更后要把窗口滑到「新的今天」（v4.80.4）。 */
    private val widgetDataSynchronizer: WidgetDataSynchronizer by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        // 与运行时注册同一个「一次 goAsync」惯例：不阻塞主线程（拉起进程 → IO + 重排 → 锁屏广播信号 → 回前台）。
        Log.d(TAG, "收到 ${intent.action}，开始重排…")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 先同步组件数据快照：日期/时区变了，快照窗口（同步日 → +6 天）与
                // 「日期 >= 今天」的日程/考试筛选口径都过期了。同步失败不阻断重排 ——
                // 闹钟纠偏比组件快照重要，快照还有 tick 与跨天锚点兜底。
                runCatching { widgetDataSynchronizer.syncNow() }
                    .onFailure { Log.e(TAG, "组件快照同步失败（保留旧快照，继续重排闹钟）", it) }

                // 跨天锚点必须重挂（v4.81.2）：它是「本地 00:00:20」换算成的 epoch 毫秒，
                // 换时区 / 改系统时间后旧闹钟仍落在旧基准上 —— 轻则翻页错时，重则跨天当天
                // 不响。AlarmManager 的 RTC 闹钟不会因时区变化自行平移，只能重排。
                runCatching {
                    if (WorkManagerHelper.hasAnyWidget(appContext)) {
                        WidgetDayBoundaryAlarmScheduler(appContext).schedule()
                    }
                }.onFailure { Log.e(TAG, "重挂跨天锚点失败（保留旧闹钟，继续重排）", it) }

                val summary = NotificationScheduler(
                    context = appContext,
                    appSettingsRepository = appSettingsRepository,
                    widgetRepository = widgetRepository
                ).reschedule()
                Log.d(
                    TAG,
                    "时间/时区/语言变更重排完成：课程 ${summary.reminderCount} 条" +
                        "，自动模式 ${summary.autoModeCount} 条，${summary.morningAlarmResult}"
                )
                // R41-09：`reschedule()` 的 failedStrategies 此前被直接丢弃。
                // 该值 > 0 表示本轮 `cancelAll()` 之后有策略抛异常 ⇒ 缺的那部分**没有闹钟**，
                // 而用户侧表现是「当日提醒真空」且不可自愈（没有任何自愈入口）。
                // 修法：失败即请求一次周期 Worker 重排（唯一任务名 + 退避策略由 WorkManager 负责），
                // 与 NotificationSyncWorker 的 retry 语义对齐。
                if (summary.failedStrategies > 0) {
                    Log.w(TAG, "有 ${summary.failedStrategies} 个策略排程失败，请求周期 Worker 补齐")
                    NotificationSyncWorker.enqueue(context = appContext)
                }
            } catch (e: Exception) {
                // 静默处理：部分系统在锁屏广播值下发期间会抛异常，重排失败无害，
                // 下次广播 / 重开 App / WorkManager 退避重试都会补上。
                Log.e(TAG, "时间/时区/语言变更重排失败（保持既有排程）", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
    }
}

/** 本 Receiver 处理的 action 全集：既作 manifest 声明的单一事实来源，也作 onReceive 的白名单。 */
internal val HANDLED_ACTIONS = setOf(
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_LOCALE_CHANGED
)
