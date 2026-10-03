package com.shangkeschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.notification.plan.WidgetRefreshEngine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 小组件「课表边界」精确闹钟的排程侧（v4.67.36）。
 *
 * 与 [com.shangkeschedule.service.notification.alarm.AlarmScheduler] 的关系：
 * 后者是**通用闹钟出口**（请求码命名空间、精确闹钟权限降级、批量注销），
 * 本类只负责「算出要在哪些时刻刷新小组件」并把它们挂上去，
 * 挂载仍走 `AlarmManager.setExactAndAllowWhileIdle` 并自带权限降级 ——
 * **不复用** [AlarmScheduler] 的取消逻辑，因为那是「每轮全量扫 61000–61199」，
 * 而小组件刷新有自己的请求码区间（64xxx），混进去会让课程提醒的注销误伤组件刷新。
 *
 * ## 请求码命名空间
 *
 * `WIDGET_REFRESH_CODE_BASE = 64_000`，与既有约定对齐：
 * 61000–61199 课程提醒 / 63000–63059 自动勿扰 / 65000 早八降级 /
 * 60001–60002 灵动岛 / 50000–50200 旧版清理。64xxx 是新增的独占段。
 *
 * ## 覆盖窗口
 *
 * 只排「今天 + 明天」两天：更远的日期由
 * [com.shangkeschedule.service.DailyRolloverWorker] 每日零点自愈重排，
 * 以及每次开 App / 每次课表变更（`SyncManager` → `reschedule`）时重排。
 */
internal class WidgetBoundaryAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * 按今天起 [WINDOW_DAYS] 天的课表重排小组件边界刷新闹钟。
     *
     * @param courses 已由 `ReminderEngine.effectiveCourses` 过滤过的有效课程
     * @param today   排程起始日
     * @param now     当前时刻（已过期的点会被跳过）
     * @return 实际挂上的闹钟条数
     */
    fun reschedule(
        courses: List<com.shangkeschedule.data.db.widget.WidgetCourse>,
        today: LocalDate,
        now: LocalDateTime
    ): Int {
        val am = alarmManager ?: return 0

        // 先清本命名空间：与 AlarmScheduler 同策略「无残留、无碰撞」。
        cancelRange(am, WIDGET_REFRESH_CODE_BASE, WIDGET_REFRESH_CODE_LIMIT)

        var count = 0
        for (offset in 0 until WINDOW_DAYS) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            val points = WidgetRefreshEngine.scheduleFor(courses, date)
            for (point in points) {
                val triggerAt = LocalDateTime(point.date, point.time)
                // 过期点直接跳过：排一个已经过去的闹钟只会立刻触发一次无用刷新。
                if (triggerAt <= now) continue
                if (!setExact(am, triggerAt, offset * WIDGET_REFRESH_CODE_LIMIT + count)) continue
                count++
                if (count >= WIDGET_REFRESH_CODE_LIMIT) {
                    Log.w(TAG, "小组件刷新闹钟已达上限 $WIDGET_REFRESH_CODE_LIMIT，多余刷新点被丢弃")
                    return count
                }
            }
        }
        return count
    }

    /**
     * 挂一个精确闹钟；缺 `SCHEDULE_EXACT_ALARM` 时降级为非精确（与 AlarmScheduler 同策略）。
     *
     * 降级在这里是**可接受**的：组件刷新迟几分不会让用户受损，
     * 而「排了但完全不响」才是真故障 —— 故不弹权限提示，避免与课程提醒的提示重复打扰。
     */
    private fun setExact(am: AlarmManager, triggerAt: LocalDateTime, requestCode: Int): Boolean = try {
        val triggerMillis = triggerAt
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            refreshIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !am.canScheduleExactAlarms()
        ) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
        true
    } catch (t: Throwable) {
        // OEM 定制下 setExactAndAllowWhileIdle 可能抛 SecurityException；
        // 单个刷新点挂不上不应中断整轮排程（其余点仍可挂）。
        Log.w(TAG, "挂小组件刷新闹钟失败 rc=$requestCode：${t.message}", t)
        false
    }

    private fun refreshIntent(): Intent =
        Intent(context, WidgetRefreshAlarmReceiver::class.java).apply {
            action = WidgetRefreshAlarmReceiver.ACTION_WIDGET_REFRESH
        }

    /** 扫掉整段请求码上的 PendingIntent（FLAG_NO_CREATE：不存在就不发多余请求）。 */
    private fun cancelRange(am: AlarmManager, base: Int, limit: Int) {
        for (offset in 0 until limit) {
            val pi = PendingIntent.getBroadcast(
                context,
                base + offset,
                refreshIntent(),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let {
                am.cancel(it)
                it.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetBoundaryAlarm"

        /** 小组件刷新闹钟请求码基址（独占段，见类 KDoc 的命名空间表）。 */
        const val WIDGET_REFRESH_CODE_BASE = 64_000

        /**
         * 上限。两天 × 每天 8 节课 × 4 个点 = 64，200 已有大量余量。
         * 留够余量是为了「未来某天排了极密课表」时不会静默截断。
         */
        const val WIDGET_REFRESH_CODE_LIMIT = 200

        /** 排程窗口：今天 + 明天。更远的日期靠每日零点自愈与开 App 重排。 */
        const val WINDOW_DAYS = 2
    }
}