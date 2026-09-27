package com.shangkeschedule.service.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.service.notification.morning.MorningAlarmWriter
import com.shangkeschedule.service.notification.notify.CourseReminderNotifier
import com.shangkeschedule.service.notification.notify.MorningAlarmNotifier
import com.shangkeschedule.service.notification.notify.PostedNotificationRegistry
import com.shangkeschedule.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 课程提醒闹钟接收器（取代旧 `CourseAlarmReceiver` 的课程提醒职责）。
 *
 * 与旧实现的区别：
 *  - **专属 action**：`ACTION_COURSE_REMINDER`，不再与自动勿扰/旧版闹钟混用同一个 action
 *    与同一个槽位空间（旧版 `EXTRA_DND_ACTION` 混挂在课程 action 上是主要混淆源）；
 *  - **投递交给 [CourseReminderNotifier]**：稳定通知 ID + 穿戴兼容双语义 + 登记回收；
 *  - **关闭按钮自持**：`ACTION_DISMISS_NOTIFICATION` 由本接收器处理，按通知 ID 精确取消；
 *  - 保留既有的「清除闹钟登记 + 刷新小组件」收尾行为。
 */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        when (intent?.action) {
            ACTION_COURSE_REMINDER -> handleReminder(ctx, intent)
            ACTION_DISMISS_NOTIFICATION -> handleDismiss(ctx, intent)
            ACTION_MORNING_ALARM_FALLBACK -> handleMorningFallback(ctx, intent)
            else -> Log.d(TAG, "忽略未知 action: ${intent?.action}")
        }
    }

    private fun handleReminder(ctx: Context, intent: Intent) {
        val course = intent.toCourse() ?: run {
            Log.w(TAG, "课程提醒 extras 缺失，投递通用提醒")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = appSettingsRepository.getAppSettingsOnce()
                val notifier = CourseReminderNotifier(ctx)
                notifier.post(course, settings.compatWearableSync)
                updateAllWidgets(ctx)
            } catch (e: Exception) {
                Log.e(TAG, "处理课程提醒失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 「关闭」按钮：只取消本条通知（稳定 ID 保证不会误关别的课）。 */
    private fun handleDismiss(ctx: Context, intent: Intent) {
        val targetId = intent.getIntExtra(EXTRA_TARGET_NOTIFICATION_ID, INVALID_ID)
        if (targetId == INVALID_ID) return
        runCatching {
            ctx.getSystemService<android.app.NotificationManager>()?.cancel(targetId)
            PostedNotificationRegistry(ctx).remove(targetId)
        }.onFailure { Log.w(TAG, "关闭通知失败 id=$targetId", it) }
    }

    /**
     * 早八闹钟**降级**触发：系统无可用时钟应用时，由应用内闹钟在早八时刻投递高优先级提醒。
     * 仅在 [MorningAlarmWriter] 判定无时钟应用时才会被排程（避免与系统闹钟双重响铃）。
     */
    private fun handleMorningFallback(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_MORNING_TITLE).orEmpty()
        val courseName = intent.getStringExtra(EXTRA_MORNING_COURSE).orEmpty()
        val startTime = intent.getStringExtra(EXTRA_MORNING_START).orEmpty()
        MorningAlarmNotifier.postFallback(ctx, title, courseName, startTime)
    }

    /** 从 Intent extras 还原课程实体（不传整个对象，避免 Parcelable 依赖与体积）。 */
    private fun Intent.toCourse(): WidgetCourse? {
        val id = getStringExtra(EXTRA_COURSE_ID) ?: return null
        val date = getStringExtra(EXTRA_COURSE_DATE) ?: return null
        return WidgetCourse(
            id = id,
            name = getStringExtra(EXTRA_COURSE_NAME).orEmpty(),
            teacher = getStringExtra(EXTRA_COURSE_TEACHER).orEmpty(),
            position = getStringExtra(EXTRA_COURSE_POSITION).orEmpty(),
            startTime = getStringExtra(EXTRA_COURSE_START).orEmpty(),
            endTime = getStringExtra(EXTRA_COURSE_END).orEmpty(),
            isSkipped = false,
            date = date,
            colorInt = getIntExtra(EXTRA_COURSE_COLOR, 0)
        )
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        private const val INVALID_ID = -1

        /** 课程提醒专属 action（与自动勿扰、旧版 action 完全隔离）。 */
        const val ACTION_COURSE_REMINDER = "com.shangkeschedule.action.COURSE_REMINDER"

        /** 通知「关闭」按钮。 */
        const val ACTION_DISMISS_NOTIFICATION = "com.shangkeschedule.action.DISMISS_COURSE_NOTIFICATION"

        /** 早八闹钟降级触发。 */
        const val ACTION_MORNING_ALARM_FALLBACK = "com.shangkeschedule.action.MORNING_ALARM_FALLBACK"

        const val EXTRA_TARGET_NOTIFICATION_ID = "target_notification_id"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_COURSE_DATE = "course_date"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_COURSE_TEACHER = "course_teacher"
        const val EXTRA_COURSE_POSITION = "course_position"
        const val EXTRA_COURSE_START = "course_start"
        const val EXTRA_COURSE_END = "course_end"
        const val EXTRA_COURSE_COLOR = "course_color"

        const val EXTRA_MORNING_TITLE = "morning_title"
        const val EXTRA_MORNING_COURSE = "morning_course"
        const val EXTRA_MORNING_START = "morning_start"
    }
}
