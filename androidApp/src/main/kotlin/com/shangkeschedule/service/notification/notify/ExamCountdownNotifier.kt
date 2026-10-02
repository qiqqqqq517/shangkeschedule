package com.shangkeschedule.service.notification.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.notification.identity.NotificationIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

/**
 * 考试倒计时提醒（补全方案 §G3）。
 *
 * 数据来自「日程」页的考试条目（[ScheduleCategory.EXAM]），**不需要新建表**。
 * 形态是**单例通知**：始终只占一个通知位，内容随最近一场考试每天重算。
 *
 * 三条设计取舍：
 *  - **窗口 7 天**：只在考试进入一周内才提醒；更远的考试投出去只会变成噪音，
 *    窗口外主动 [cancel]，避免留下一个早已过期的倒计时。
 *  - **`setOnlyAlertOnce(true)`**：首次出现时响/震一次，之后每天的更新静默 ——
 *    它的价值是「抬眼可见」，不是每天准点再来打扰一次。
 *  - **不注册进 `PostedNotificationRegistry`**：那是课程提醒的回收账本，
 *    `CourseReminderScheduler` 的 `pruneExcept` 会把它人的条目一并取消。
 */
class ExamCountdownNotifier(private val context: Context) {

    /**
     * 读日程并整算提醒。
     *
     * 读库超时/异常时**保留上一条**（不 cancel）——宁可显示一条略旧的倒计时，
     * 也不要在数据源抖动时把提醒清空。
     */
    suspend fun sync(enabled: Boolean, today: LocalDate) {
        if (!enabled) {
            cancel()
            return
        }

        val events = withTimeoutOrNull(READ_TIMEOUT) {
            runCatching { Deps.scheduleEventRepository.getAllEvents().first() }
                .onFailure { Log.e(TAG, "读取考试日程失败", it) }
                .getOrNull()
        }
        if (events == null) {
            Log.w(TAG, "考试日程读取超时或失败，本次保留既有提醒")
            return
        }

        val nearest = events
            .asSequence()
            .filter { ScheduleCategory.fromKey(it.category) == ScheduleCategory.EXAM }
            .filter { !it.done }
            .filter { it.date >= today.toString() }
            .sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
            .firstOrNull()

        if (nearest == null) {
            cancel()
            return
        }

        // 用 java.time 只做「相差几天」这一步：`ScheduleEvent.date` 是 "yyyy-MM-dd" 字符串，
        // 与 kotlinx.datetime 的 LocalDate 混用时显式转换最不容易出错。
        val days = runCatching {
            ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(today.toString()),
                java.time.LocalDate.parse(nearest.date)
            )
        }.getOrNull()
        if (days == null || days > REMIND_WINDOW_DAYS) {
            cancel()
            return
        }

        post(nearest.title, days.toInt(), nearest.date, nearest.startTime, nearest.location)
    }

    /** 撤下提醒（关闭开关 / 无考试 / 超出窗口）。 */
    fun cancel() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NotificationIds.EXAM_COUNTDOWN_ID)
        }.onFailure { Log.w(TAG, "取消考试倒计时提醒失败: ${it.message}") }
    }

    private fun post(
        examTitle: String,
        days: Int,
        date: String,
        startTime: String?,
        location: String?
    ) {
        if (!hasPermission()) {
            Log.w(TAG, "通知权限未授予，考试倒计时提醒无法投递")
            return
        }
        runCatching {
            NotificationChannels.ensureAll(context)
            val name = examTitle.ifBlank { context.getString(R.string.notification_exam_countdown_title) }
            val head = when (days) {
                0 -> context.getString(R.string.notification_exam_countdown_today, name)
                1 -> context.getString(R.string.notification_exam_countdown_tomorrow, name)
                else -> context.getString(R.string.notification_exam_countdown_days, name, days)
            }
            val meta = listOfNotNull(date, startTime, location)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            val text = if (meta.isBlank()) head else "$head\n$meta"
            val notification = NotificationCompat.Builder(context, NotificationChannels.EXAM_COUNTDOWN)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_exam_countdown_title))
                .setContentText(head)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent())
                .build()
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.EXAM_COUNTDOWN_ID, notification)
        }.onFailure { Log.e(TAG, "投递考试倒计时提醒失败", it) }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.EXAM_COUNTDOWN_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * 局部 Koin 代理（与 `NotificationSchedulerDeps`/`WidgetDependencyContainer` 同构）：
     * 这样就不必为了拿一个仓库去改 `NotificationScheduler` 的构造签名。
     */
    private object Deps : KoinComponent {
        val scheduleEventRepository: ScheduleEventRepository by inject()
    }

    private companion object {
        const val TAG = "ExamCountdownNotifier"

        /** 提前提醒窗口：只在考试进入 7 天内才投递。 */
        const val REMIND_WINDOW_DAYS = 7L

        /** 读库超时：宁可保留旧提醒，也不让通知链路卡住。 */
        val READ_TIMEOUT = 3.seconds
    }
}
