package com.shangkeschedule.service.notification.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.R
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.identity.NotificationIds
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.control.AutoModeController
import com.shangkeschedule.service.notification.control.AutoModeStateProbe
import com.shangkeschedule.service.notification.morning.MorningAlarmWriter
import com.shangkeschedule.service.notification.notify.NotificationChannels
import com.shangkeschedule.service.notification.notify.PostedNotificationRegistry
import com.shangkeschedule.service.notification.receiver.AutoModeAlarmReceiver
import com.shangkeschedule.service.notification.receiver.ReminderAlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 通知/自动化**唯一编排入口**。
 *
 * 取代旧版三处各自为政的调度：
 *  - `CourseNotificationWorker`（课程提醒闹钟）
 *  - `DndSchedulerWorker`（上课自动勿扰）
 *  - `DynamicIslandManager`（灵动岛窗口，其窗口计算已归一到 [ReminderEngine]）
 *
 * 现在调用方只表达一个意图——「设置/课表变了，重新排一遍」，具体排什么由本类决定：
 *  1. [ReminderEngine] 算出课程提醒时刻与自动模式切换时刻；
 *  2. 闹钟请求码经 [AlarmScheduler] 顺序分配，每轮**先全量注销再重挂**（无残留、无碰撞）；
 *  3. 已投递但已失效的提醒通知由 [PostedNotificationRegistry] 回收；
 *  4. 早八闹钟交给 [MorningAlarmWriter] 落到系统时钟（或降级）。
 *
 * 本类**只做编排**：算时刻、构造业务 Intent、决定排什么。
 * 系统闹钟的挂载/注销与请求码编号全部下沉到 [AlarmScheduler]。
 *
 * ## 修复的旧缺陷
 *  - **通知永久残留**：旧 `cancelAllAlarms` 只取消闹钟、不取消通知；
 *  - **设置读取竞态**：旧 `DndSchedulerWorker` 用 `getAppSettings().first()`（热流，replay 可能落后），
 *    统一改走 `getAppSettingsOnce()` 冷读；
 *  - **状态卡死**：旧 `isCurrentlyInDndTime()` 的结果只 `Log.d` 从不应用，
 *    错下课闹钟后模式会卡到下次同步——现在每次重排都按 [ReminderEngine.shouldModeBeOn] 校准；
 *  - **重复排程**：三处各自读库计算，行为漂移。
 */
class NotificationScheduler(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository
) {

    /** 系统闹钟层的唯一出口（请求码分配 + 挂/销）。 */
    private val alarms = AlarmScheduler(context)

    private val registry = PostedNotificationRegistry(context)

    /**
     * 全量重排。
     *
     * @return 排程概要，供调用方记录/展示
     */
    suspend fun reschedule(): Summary {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val today = now.date

        // 单次读库，供三套调度共用（旧版各读一次，且读的是不同的日期范围）
        val courses = runCatching {
            widgetRepository.getWidgetCoursesByDateRange(
                today.toString(),
                today.plus(WINDOW_DAYS, DateTimeUnit.DAY).toString()
            ).first()
        }.getOrElse { error ->
            // 读库失败：**保留**旧闹钟（不清空），下轮重试。
            // 旧实现先清后读，读库异常会把全部提醒槽位清掉且不重试。
            Log.e(TAG, "读取课程失败，保留既有排程", error)
            return Summary(readFailed = true)
        }

        val effective = ReminderEngine.effectiveCourses(courses, settings.skippedDates)

        alarms.cancelAll()
        NotificationChannels.ensureAll(context)

        val reminderCount = if (settings.reminderEnabled) {
            scheduleCourseReminders(effective, settings.remindBeforeMinutes, now)
        } else {
            // 总开关关闭：连带回收站内提醒（旧实现只清闹钟，通知留在状态栏）
            registry.clear()
            0
        }

        val autoModeCount = if (settings.autoModeEnabled) {
            scheduleAutoMode(effective, now)
        } else {
            0
        }

        // 状态校准：把「该开未开 / 该关未关」的卡态对齐（旧版校准结果只打日志）
        reconcileAutoModeState(settings, effective, now)

        val morningResult = syncMorningAlarm(settings, courses, today, now)

        return Summary(
            reminderCount = reminderCount,
            autoModeCount = autoModeCount,
            morningAlarmResult = morningResult,
            readFailed = false
        )
    }

    // ---------------------------------------------------------------------
    // 课程提醒
    // ---------------------------------------------------------------------

    private fun scheduleCourseReminders(
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

    // ---------------------------------------------------------------------
    // 自动勿扰 / 静音
    // ---------------------------------------------------------------------

    private fun scheduleAutoMode(courses: List<WidgetCourse>, now: LocalDateTime): Int {
        val transitions = ReminderEngine.autoModeTransitions(courses)
            .filter { LocalDateTime(it.date, it.time) > now }
            .take(alarms.autoModeSlotLimit)

        transitions.forEachIndexed { index, transition ->
            val triggerAt = LocalDateTime(transition.date, transition.time)
            val action = if (transition.enable) {
                AutoModeAlarmReceiver.ACTION_AUTO_MODE_START
            } else {
                AutoModeAlarmReceiver.ACTION_AUTO_MODE_END
            }
            val intent = Intent(context, AutoModeAlarmReceiver::class.java).apply {
                this.action = action
            }
            alarms.setAutoMode(intent, index, triggerAt)
        }
        return transitions.size
    }

    /**
     * 状态校准：若实际模式状态与「当前是否在上课」不一致，就地纠正。
     *
     * 场景：下课时刻的 END 闹钟因设备重启/进程被杀而丢失 → 模式卡在勿扰。
     * 旧实现算了这个判断却只写日志，用户只能等下一次同步（甚至次日）。
     */
    private fun reconcileAutoModeState(
        settings: AppSettingsModel,
        courses: List<WidgetCourse>,
        now: LocalDateTime
    ) {
        if (!settings.autoModeEnabled) return
        val shouldBeOn = ReminderEngine.shouldModeBeOn(
            courses = courses,
            skippedDates = settings.skippedDates,
            date = now.date,
            time = now.time
        )
        val currentlyOn = AutoModeStateProbe.isModeOn(context, settings.autoControlMode)
        if (shouldBeOn == currentlyOn) return

        Log.i(TAG, "自动模式状态校准：当前=$currentlyOn 应为=$shouldBeOn")
        AutoModeController.toggle(context, shouldBeOn, settings.autoControlMode)
    }

    // ---------------------------------------------------------------------
    // 早八闹钟
    // ---------------------------------------------------------------------

    private fun syncMorningAlarm(
        settings: AppSettingsModel,
        courses: List<WidgetCourse>,
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
            days = WINDOW_DAYS,
            leadMinutes = settings.morningAlarmLeadMinutes
        )
        val result = MorningAlarmWriter(context, now).sync(plan)
        if (result is MorningAlarmWriter.Result.NoClockApp) {
            // 系统没有可用时钟应用：降级为应用内闹钟（高优先级 + 闹钟铃声通知），
            // 否则用户开了早八闹钟却什么也收不到。
            scheduleMorningAlarmFallback(plan, now)
        }
        return result
    }

    /**
     * 早八降级：只排最近一条应用内闹钟。
     *
     * 与系统闹钟路径**互斥**（仅在无时钟应用时进入），因此不会双重响铃。
     * 仅排最近一条：应用内闹钟不跨重启持久，且每日零点自愈与前台刷新会滚动补位。
     */
    private fun scheduleMorningAlarmFallback(
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

    /** 排程概要。 */
    data class Summary(
        val reminderCount: Int = 0,
        val autoModeCount: Int = 0,
        val morningAlarmResult: MorningAlarmWriter.Result? = null,
        /** 读库失败（已保留旧排程，等下轮重试） */
        val readFailed: Boolean = false
    )

    companion object {
        private const val TAG = "NotificationScheduler"

        /** 前瞻天数（与原 7 天窗口一致）。 */
        const val WINDOW_DAYS = 7
    }
}
