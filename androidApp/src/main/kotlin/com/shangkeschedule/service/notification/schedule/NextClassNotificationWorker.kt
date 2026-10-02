package com.shangkeschedule.service.notification.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.notify.NextClassNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.android.annotation.KoinWorker
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * 「下一节课」常驻通知的**周期刷新**（补全方案 §G1）。
 *
 * 为什么需要它：闹钟/课程提醒只在预设时刻触发，而常驻通知的「剩余分钟」是**连续变化**的，
 * 没课的时候还要能把内容换成「明日第一节」或把自己撤掉。WorkManager 周期任务
 * （15 分钟，系统最小周期）正好覆盖这个粒度；界面上的分钟数不追求秒级精确，
 * 它是一块状态牌，不是倒计时器。
 *
 * 与 `NotificationScheduler.reschedule()` 的分工：那条链路负责「设置/课表变了，立刻重排」，
 * 本 Worker 负责「什么都没变，但时间过去了」。因此：
 *  - 开关打开时由 `reschedule()` 注册（`KEEP`，不打断已排队的那一次）；
 *  - 开关关闭或用户点通知里的「关闭常驻」时注销 —— 不留空转任务。
 */
@KoinWorker
class NextClassNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = appSettingsRepository.getAppSettingsOnce()
        if (!settings.nextClassNotificationEnabled) {
            // 开关已被关掉（可能是在另一台设备/设置页关的）：顺手撤下并结束这一轮。
            NextClassNotifier(applicationContext).cancel()
            return Result.success()
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        // 只需今天 + 明天：常驻通知最多显示到「明日第一节」。
        // 读库加 3s 超时兜底（与 WidgetUpdateHelper / ExamCountdownNotifier 同纪律）：
        // 底层 IO 卡住时不能让这一轮 Worker 一直挂着。
        val courses = runCatching {
            withTimeoutOrNull(READ_TIMEOUT_MS) {
                widgetRepository.getWidgetCoursesByDateRange(
                    today.toString(),
                    today.plus(1, DateTimeUnit.DAY).toString()
                ).first()
            }
        }.getOrNull()

        if (courses == null) {
            // 读库失败/超时：**保留**上一条通知（不清空）。重试有限次后放弃本轮
            //（周期任务下一轮自然重来），避免失败路径无上限重试。
            Log.e(TAG, "读取课程失败或超时，保留既有常驻通知")
            return if (runAttemptCount < MAX_READ_ATTEMPTS) Result.retry() else Result.failure()
        }

        NextClassNotifier(applicationContext).sync(
            courses = ReminderEngine.effectiveCourses(courses, settings.skippedDates),
            today = today,
            nowMinutes = now.hour * 60 + now.minute,
            requestChip = !settings.compatWearableSync
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "NextClassNotificationWorker"

        /** 读库超时：超时按失败处理，保留上一条通知（与其它读库路径同纪律）。 */
        private const val READ_TIMEOUT_MS = 3_000L

        /** 读库失败的最大重试轮数，超过后放弃本轮（周期任务下一轮重来）。 */
        private const val MAX_READ_ATTEMPTS = 3

        /** 唯一任务名：单实例周期任务。 */
        const val UNIQUE_WORK_NAME = "NextClassNotificationWorker_Periodic"

        /** 刷新周期。15 分钟是 WorkManager 周期任务的下限。 */
        private const val REFRESH_INTERVAL_MINUTES = 15L

        /** 注册周期刷新（已排队则保持原样，避免每次设置同步都重置计时）。 */
        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<NextClassNotificationWorker>(
                    REFRESH_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { Log.w(TAG, "注册「下一节课」周期刷新失败: ${it.message}") }
        }

        /** 注销周期刷新。 */
        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            }.onFailure { Log.w(TAG, "注销「下一节课」周期刷新失败: ${it.message}") }
        }
    }
}
