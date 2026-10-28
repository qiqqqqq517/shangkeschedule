package com.shangkeschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shangkeschedule.notification.plan.WidgetRefreshEngine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 小组件「跨天锚点」精确闹钟的排程侧（v4.80.4）。
 *
 * ## 它解决什么（跨天不同步的根因）
 *
 * 小组件此前在**零点前后没有任何确定性刷新点**，跨天只能靠两条**不保证及时**的路径：
 *
 * 1. `WidgetUiUpdateWorker`（15 分钟 tick，WorkManager 周期任务）—— Doze 下会被推迟；
 * 2. `DailyRolloverWorker`（00:05 一次任务，WorkManager）—— 同样是**不精确**的延迟任务，
 *    夜间静止 + 未充电时进入 Doze 后可被推迟到数小时之后。
 *
 * 而唯一的「零点整」触发点 `android.intent.action.DATE_CHANGED` **投递不到 manifest 接收器**：
 * 该 action 不在官方隐式广播豁免清单内（清单收录 `TIME_SET` / `TIMEZONE_CHANGED` /
 * `LOCALE_CHANGED`，**没有** `DATE_CHANGED`），且 AOSP 的发送方也未带
 * `FLAG_RECEIVER_INCLUDE_BACKGROUND`（android10-release `AlarmManagerService`：
 * `new Intent(ACTION_DATE_CHANGED)` + `FLAG_RECEIVER_REPLACE_PENDING |
 * FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS`）。targetSdk 26+ 时它不会送到本应用。
 * 于是零点后桌面**继续显示昨天的课表**，直到首个课程边界点（早八前 15 分 = 07:45）
 * 或某次被推迟的 tick 真正跑起来 —— 与「不打开 App 就能看到课表」的产品语义直接冲突。
 *
 * ## 与课程边界闹钟的关系
 *
 * | 排程器 | 触发时刻 | 负责 |
 * | --- | --- | --- |
 * | [WidgetBoundaryAlarmScheduler] | 每节课的 4 个状态翻转点 | 「正在上课 / 下一节课」准时翻转 |
 * | 本类 | 每日 [WidgetRefreshEngine.DAY_BOUNDARY_TIME] | 跨天：**同步数据** + 重绘全部组件 |
 *
 * 两者职责不同、不能互相替代：边界点全部**相对课程**，当天无课或首节课在下午时，
 * 零点到首节课之间一个点都没有；跨天锚点则与课表内容无关，每天都在同一时刻落地。
 *
 * ## 请求码命名空间
 *
 * 取 `WIDGET_REFRESH_CODE_BASE + WIDGET_REFRESH_CODE_LIMIT`（= **64200**）：
 * 课程边界段是 64000–64199（[WidgetBoundaryAlarmScheduler.cancelRange] 逐码扫这一段），
 * 64200 是段外第一个码 —— 既不会被边界重排误撤，也不会被 `allocate` 分配（其上限是 64199）。
 * 改用「基址 + 上限」而不是写死 64200，是为了让这层关系在代码里可自证：
 * 边界段的容量一旦调整，跨天码跟着让位。
 *
 * ## 幂等与自续
 *
 * 同一个 [REQUEST_CODE] + `FLAG_UPDATE_CURRENT`，每次排程都**顶掉**上一次；
 * 闹钟触发后由 [WidgetDayBoundaryReceiver] 再排明天 —— 与 `DailyRolloverWorker`
 * 的「执行完自续链到次日」同一套语义。
 */
internal class WidgetDayBoundaryAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 排定「下一个跨天锚点」（严格晚于 [now]）。
     *
     * @return 是否成功挂上（无 AlarmManager / OEM 抛 SecurityException 时为 false，
     *         不抛出：跨天闹钟挂不上只应退化为「回到 tick 兜底」，不该让调用方（开机、
     *         应用启动、添加组件）失败）。
     */
    fun schedule(now: LocalDateTime = currentLocalDateTime()): Boolean {
        val am = alarmManager ?: return false
        val triggerAt = WidgetRefreshEngine.nextDayBoundary(now)
        val triggerMillis = runCatching {
            triggerAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }.getOrElse {
            Log.w(TAG, "跨天锚点时刻换算失败：$triggerAt", it)
            return false
        }

        return try {
            val pi = pendingIntent()
            // 与边界闹钟同策略：缺 SCHEDULE_EXACT_ALARM 时降级为非精确。
            // 降级在这里可接受 —— 组件迟几分钟翻页远好于「排了但完全不响」。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
            Log.d(TAG, "跨天锚点已排定：$triggerAt")
            true
        } catch (t: Throwable) {
            // OEM 定制下 setExactAndAllowWhileIdle 可能抛 SecurityException
            Log.w(TAG, "挂跨天锚点失败（$triggerAt）：${t.message}", t)
            false
        }
    }

    /** 撤下待触发的跨天锚点（桌面已无任何组件时调用，避免为无组件用户多唤醒一次）。 */
    fun cancel() {
        val am = alarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            refreshIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        refreshIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun refreshIntent(): Intent =
        Intent(context, WidgetDayBoundaryReceiver::class.java).apply {
            action = ACTION_WIDGET_DAY_BOUNDARY
        }

    /** 当前本地时刻（kotlinx 口径，与 [WidgetRefreshEngine] 的纯逻辑一致）。 */
    private fun currentLocalDateTime(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    internal companion object {
        private const val TAG = "WidgetDayBoundary"

        /**
         * 跨天锚点请求码：**课程边界段（64000–64199）之后第一个码**。
         * 见类 KDoc「请求码命名空间」。
         */
        val REQUEST_CODE: Int =
            WidgetBoundaryAlarmScheduler.WIDGET_REFRESH_CODE_BASE +
                WidgetBoundaryAlarmScheduler.WIDGET_REFRESH_CODE_LIMIT

        /** 本接收器的专属 action（与课程边界刷新的 action 完全隔离）。 */
        const val ACTION_WIDGET_DAY_BOUNDARY =
            "com.shangkeschedule.action.WIDGET_DAY_BOUNDARY"
    }
}
