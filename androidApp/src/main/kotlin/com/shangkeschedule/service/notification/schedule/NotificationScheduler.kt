package com.shangkeschedule.service.notification.schedule

import android.content.Context
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.control.AutoModeScheduler
import com.shangkeschedule.service.notification.morning.MorningAlarmScheduler
import com.shangkeschedule.service.notification.morning.MorningAlarmWriter
import com.shangkeschedule.service.notification.notify.NotificationChannels
import com.shangkeschedule.service.notification.reminder.CourseReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 通知/自动化**唯一编排入口**：只回答「这一轮该排什么」，怎么排交给各作用策略。
 *
 * 取代旧版三处各自为政的调度：
 *  - `CourseNotificationWorker`（课程提醒闹钟）
 *  - `DndSchedulerWorker`（上课自动勿扰）
 *  - `DynamicIslandManager`（灵动岛窗口，其窗口计算已归一到 [ReminderEngine]）
 *
 * 现在调用方只表达一个意图——「设置/课表变了，重新排一遍」。
 *
 * ## 按作用分层
 * | 层 | 作用 | 归属 |
 * |---|---|---|
 * | 0 | 编排：读设置/课表 → 先全量注销再重挂 → 汇总 | 本类 |
 * | 1 | 课程提醒排程 + 失效提醒回收 | [CourseReminderScheduler] |
 * | 1 | 自动勿扰/静音排程 + 卡态校准 | [AutoModeScheduler] |
 * | 1 | 早八闹钟落系统时钟 / 降级 | [MorningAlarmScheduler] |
 * | 2 | 系统闹钟底层：请求码分配 + 挂/销 | [AlarmScheduler] |
 * | 2 | 纯计算：提醒时刻 / 切换序列 / 模式判定 | [ReminderEngine]（shared） |
 *
 * 本类**只做编排与汇总**：算时刻、构造业务 Intent、决定排什么全部下沉到上面三层。
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

    /** 系统闹钟层的唯一出口；三个策略**共用同一实例**，保证请求码编号全局唯一。 */
    private val alarms = AlarmScheduler(context)

    private val courseReminders = CourseReminderScheduler(context, alarms)

    private val autoMode = AutoModeScheduler(context, alarms)

    private val morningAlarms = MorningAlarmScheduler(context, alarms)

    /**
     * 全量重排。
     *
     * 顺序固定：**先全量注销**（无残留、无碰撞）→ 确保通知渠道 → 三套策略各自排 →
     * 汇总条数返回。任一套失败都不该影响其余（各策略内部自吞异常/自行降级）。
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

        // 三套策略共用同一份「有效课程」（已剔跳过日、已按时间排序）
        val effective = ReminderEngine.effectiveCourses(courses, settings.skippedDates)

        alarms.cancelAll()
        NotificationChannels.ensureAll(context)

        val reminderCount = courseReminders.sync(settings, effective, now)
        val autoModeCount = autoMode.sync(settings, effective, now)
        // 早八要自己判断「某天是否还有课」，故传**原始**课程 + 窗口天数
        val morningResult = morningAlarms.sync(settings, courses, WINDOW_DAYS, today, now)

        return Summary(
            reminderCount = reminderCount,
            autoModeCount = autoModeCount,
            morningAlarmResult = morningResult,
            readFailed = false
        )
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
