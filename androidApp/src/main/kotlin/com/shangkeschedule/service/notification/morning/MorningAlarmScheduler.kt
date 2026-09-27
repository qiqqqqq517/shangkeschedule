package com.shangkeschedule.service.notification.morning

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.R
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.receiver.ReminderAlarmReceiver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * 早八闹钟**排程策略**：把「当天第一节课 − 提前量」落到系统时钟，无可用时钟应用时降级为应用内闹钟。
 *
 * 从 `NotificationScheduler` 按作用拆出。注意本类拿到的是**原始课程列表**（不是
 * `ReminderEngine.effectiveCourses` 的结果）：[MorningAlarmPlan.plan] 自己按 `skippedDates`
 * 过滤，且需要判断「某天是否还有课」来决定要不要写闹钟。
 *
 * 写入本身（前台门禁、登记簿、只写最近一条）全在 [MorningAlarmWriter]；本类只负责
 * 「取开关 → 算计划 → 写入 → 无时钟应用时降级」这条决策链。
 */
internal class MorningAlarmScheduler(
    private val context: Context,
    private val alarms: AlarmScheduler
) {

    /**
     * @param courses 原始课程（计划内部自行按 `skippedDates` 过滤）
     * @param days    前瞻天数（由编排层传入，与读库窗口一致）
     * @return 写入结果；开关关闭时返回 null（表示「本轮不涉及」）
     */
    fun sync(
        settings: AppSettingsModel,
        courses: List<WidgetCourse>,
        days: Int,
        today: LocalDate,
        now: LocalDateTime
    ): MorningAlarmWriter.Result? {
        // 关闭开关：不假装删除（系统时钟删不掉），但**保留登记簿**——
        // 否则重新开启时这几天会被当成「未登记」再写一次 ⇒ 重复闹钟（真机实测非幂等）。
        if (!settings.morningAlarmEnabled) {
            MorningAlarmWriter(context, now).markDisabled()
            return null
        }
        val plan = MorningAlarmPlan.plan(
            courses = courses,
            skippedDates = settings.skippedDates,
            today = today,
            days = days,
            leadMinutes = settings.morningAlarmLeadMinutes
        )
        val result = MorningAlarmWriter(context, now).sync(plan)
        if (result is MorningAlarmWriter.Result.NoClockApp) {
            // 系统没有可用时钟应用：降级为应用内闹钟（高优先级 + 闹钟铃声通知），
            // 否则用户开了早八闹钟却什么也收不到。
            scheduleFallback(plan, now)
        }
        return result
    }

    /**
     * 早八降级：只排最近一条应用内闹钟。
     *
     * 与系统闹钟路径**互斥**（仅在无时钟应用时进入），因此不会双重响铃。
     * 仅排最近一条：应用内闹钟不跨重启持久，且每日零点自愈与前台刷新会滚动补位。
     */
    private fun scheduleFallback(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        now: LocalDateTime
    ): Boolean {
        val next = MorningAlarmPlan.nextUpcoming(plan, now) ?: run {
            Log.d(TAG, "早八降级：无未来闹钟可排")
            return false
        }
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_MORNING_ALARM_FALLBACK
            putExtra(
                ReminderAlarmReceiver.EXTRA_MORNING_TITLE,
                context.getString(R.string.notification_title_morning_alarm)
            )
            putExtra(ReminderAlarmReceiver.EXTRA_MORNING_COURSE, next.courseName)
            putExtra(ReminderAlarmReceiver.EXTRA_MORNING_START, MorningAlarmPlan.formatTime(next.courseStart))
        }
        alarms.setMorningFallback(intent, LocalDateTime(next.alarmDate, next.alarmTime))
        Log.i(TAG, "早八降级闹钟已排：${next.alarmDate} ${next.alarmTime}")
        return true
    }

    private companion object {
        const val TAG = "MorningAlarmScheduler"
    }
}
