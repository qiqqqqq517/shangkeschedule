package com.shangkeschedule.service.notification.reminder

import android.content.Context
import android.content.Intent
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.notification.identity.NotificationIds
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.notify.PostedNotificationRegistry
import com.shangkeschedule.service.notification.receiver.ReminderAlarmReceiver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * 课程提醒**排程策略**：把「每节课开始前 N 分钟」挂成系统闹钟，并回收已失效的站内提醒。
 *
 * 从 `NotificationScheduler` 按作用拆出，只负责课程提醒这一件事：
 *  1. 用 [ReminderEngine.reminderDateTime] 算每节课的提醒时刻，已过期的跳过；
 *  2. 请求码经 [AlarmScheduler] 按 occurrence 身份分配（同一次课恒得同一个码，便于精确撤销）；
 *  3. 已投递、但本轮不再计划的提醒通知由 [PostedNotificationRegistry] 回收
 *     —— 旧实现只 `AlarmManager.cancel()` 不 `NotificationManager.cancel()`，提醒会永久残留状态栏。
 *
 * 总开关关闭时**连带回收**站内提醒，而不只是「不排新闹钟」。
 */
internal class CourseReminderScheduler(
    private val context: Context,
    private val alarms: AlarmScheduler
) {

    private val registry = PostedNotificationRegistry(context)

    /**
     * 按总开关排程课程提醒。
     *
     * @param courses 已由 [ReminderEngine.effectiveCourses] 过滤过的有效课程
     * @param now     当前时刻（用于丢弃已过期的提醒时刻）
     * @return 本轮实际挂上的提醒条数
     */
    fun sync(settings: AppSettingsModel, courses: List<WidgetCourse>, now: LocalDateTime): Int {
        if (!settings.reminderEnabled) {
            // 总开关关闭：连带回收站内提醒（旧实现只清闹钟，通知留在状态栏）
            registry.clear()
            return 0
        }
        return schedule(courses, settings.remindBeforeMinutes, now)
    }

    private fun schedule(
        courses: List<WidgetCourse>,
        leadMinutes: Int,
        now: LocalDateTime
    ): Int {
        // 本轮仍需保留的通知（用于回收已失效的）
        val stillValidKeys = mutableSetOf<String>()
        var scheduled = 0

        for (course in courses) {
            if (alarms.usedSlots >= alarms.totalSlots) break
            val date = runCatching { LocalDate.parse(course.date) }.getOrNull() ?: continue
            val (reminderDate, reminderTime) =
                ReminderEngine.reminderDateTime(course, date, leadMinutes) ?: continue
            val triggerAt = LocalDateTime(reminderDate, reminderTime)
            if (triggerAt <= now) continue

            val key = NotificationIds.occurrenceKey(course)
            val code = alarms.codeFor(key) ?: break
            stillValidKeys += key
            alarms.setExact(applicationIntent(course), code, triggerAt)
            scheduled++
        }

        // 回收：已不在本轮计划里的提醒通知（课程被删/改期/已上完）
        registry.pruneExcept(stillValidKeys)
        return scheduled
    }

    /** 课程提醒广播的 Intent（接收器 = [ReminderAlarmReceiver]）。 */
    private fun applicationIntent(course: WidgetCourse): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_COURSE_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_ID, course.id)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_DATE, course.date)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_NAME, course.name)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_TEACHER, course.teacher)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_POSITION, course.position)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_START, course.startTime)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_END, course.endTime)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_COLOR, course.colorInt)
        }
}
