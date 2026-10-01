package com.shangkeschedule.service.notification.schedule

import android.content.Context
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.DynamicIslandManager
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
     * 灵动岛窗口排程器（@Single Koin）。
     *
     * 取不到就当作「本轮不管灵动岛」——它是独立的前台服务策略，缺席不应让
     * 提醒/勿扰/早八三套排程失败，故此处惰性 + 静默降级。
     */
    private val dynamicIsland: DynamicIslandManager? by lazy {
        runCatching { NotificationSchedulerDeps.dynamicIslandManager }
            .onFailure { Log.w(TAG, "灵动岛排程器不可用，本轮跳过窗口重排: ${it.message}") }
            .getOrNull()
    }

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

        // KDoc 承诺「任一套失败都不该影响其余（各策略内部自吞异常/自行降级）」，
        // 但三者此前是裸调用：任一抛异常（如 OEM 定制下 setExactAndAllowWhileIdle
        // 抛 SecurityException）都会中断整轮 —— 而 `alarms.cancelAll()` 已执行完，
        // 结果是「当日提醒真空」，要等 Worker 退避重试或下次前台同步才补上。
        var failedStrategies = 0
        fun <T> guard(tag: String, block: () -> T): T? =
            runCatching(block)
                .onFailure {
                    failedStrategies++
                    Log.e(TAG, "$tag 排程失败，其余策略继续", it)
                }
                .getOrNull()

        val reminderCount = guard("课程提醒") { courseReminders.sync(settings, effective, now) } ?: 0
        val autoModeCount = guard("自动模式") { autoMode.sync(settings, effective, now) } ?: 0
        // 早八要自己判断「某天是否还有课」，故传**原始**课程 + 窗口天数
        val morningResult = guard("早八闹钟") {
            morningAlarms.sync(settings, courses, WINDOW_DAYS, today, now)
        }

        // 灵动岛窗口此前**没有任何外部触发点**（只靠自己的 START 闹钟自举，而 START 闹钟
        // 又要靠本方法排出来）→ 开机 / 设置变更 / 课表变更后窗口永远不重排。
        // KDoc 早已声明「由 SyncManager 在同步完成时调用」，这里补上缺失的那一环。
        runCatching { dynamicIsland?.sync() }
            .onFailure { Log.w(TAG, "灵动岛窗口重排失败，不影响其余排程: ${it.message}") }

        return Summary(
            reminderCount = reminderCount,
            autoModeCount = autoModeCount,
            morningAlarmResult = morningResult,
            readFailed = false,
            failedStrategies = failedStrategies
        )
    }

    /** 排程概要。 */
    data class Summary(
        val reminderCount: Int = 0,
        val autoModeCount: Int = 0,
        val morningAlarmResult: MorningAlarmWriter.Result? = null,
        /** 读库失败（已保留旧排程，等下轮重试） */
        val readFailed: Boolean = false,
        /**
         * 本轮抛异常而未能完成排程的策略数（0..3）。
         * 非零表示这轮是「残缺」的（已排的仍在、缺的没排），调用方可据此决定重试。
         */
        val failedStrategies: Int = 0
    )

    companion object {
        private const val TAG = "NotificationScheduler"

        /** 前瞻天数（与原 7 天窗口一致）。 */
        const val WINDOW_DAYS = 7
    }
}

/**
 * 本类的 Koin 取用点（与 [WidgetUpdateHelper] 的 WidgetDependencyContainer 同构）：
 * `NotificationScheduler` 由调用方 new 出来（要传 context），不是 Koin 管理的实例，
 * 故用局部注入代理取 @Single 的 [DynamicIslandManager]。
 */
private object NotificationSchedulerDeps : KoinComponent {
    val dynamicIslandManager: DynamicIslandManager by inject()
}
