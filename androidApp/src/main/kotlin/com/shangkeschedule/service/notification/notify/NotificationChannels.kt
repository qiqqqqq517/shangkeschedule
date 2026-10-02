package com.shangkeschedule.service.notification.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService
import com.shangkeschedule.R

/**
 * 通知渠道统一定义与创建（渠道集中一处，避免散落各处 createNotificationChannel）。
 *
 * **渠通 ID 全部保持与旧版一致**：Android 上渠道的重要性/声音等属性由用户掌控，
 * 一旦换个新 ID 就等于把用户的渠道自定义（静音、置顶、重要性）全部重置。
 * 因此重写只改代码结构，不动已发布渠道的身份。
 */
object NotificationChannels {

    /** 课程提醒渠道（旧 ID，保持不变）。 */
    const val COURSE = "course_notification_channel"

    /** 权限缺失提示渠道（旧 ID，保持不变）。 */
    const val PERMISSION_NOTICE = "permission_notice_channel"

    /** 灵动岛前台服务渠道（旧 ID，保持不变；其创建仍留在 DynamicIslandService）。 */
    const val DYNAMIC_ISLAND = "dynamic_island_v2_channel"

    /**
     * 早八闹钟**降级**渠道（新增）：
     * 仅当系统无可用时钟应用时才启用——此时用应用内高优先级提醒代替系统闹钟，
     * 因此渠道需要响铃与震动（对齐系统闹钟的体感）。
     */
    const val MORNING_ALARM_FALLBACK = "morning_alarm_fallback_channel"

    /**
     * 「下一节课」常驻通知渠道（新增）：
     * 常年挂在状态栏的状态牌，因此用 IMPORTANCE_LOW —— 默认不响不震（用户仍可在
     * 系统设置里抬高重要性）；渠道名/描述里明确写「常驻」，让用户知道怎么关掉它。
     */
    const val NEXT_CLASS = "next_class_persistent_channel"

    /**
     * 考试倒计时提醒渠道（新增）：
     * IMPORTANCE_DEFAULT —— 按系统默认强度提醒一次（通知本身 `setOnlyAlertOnce`），
     * 不必像课程提醒那样抢占高优先级。
     */
    const val EXAM_COUNTDOWN = "exam_countdown_reminder_channel"

    /** 确保所有渠道存在；可重复调用（已存在则跳过，不会覆盖用户设置）。 */
    fun ensureAll(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        ensureCourseChannel(context, nm)
        ensurePermissionChannel(context, nm)
        ensureMorningAlarmChannel(context, nm)
        ensureNextClassChannel(context, nm)
        ensureExamCountdownChannel(context, nm)
    }

    private fun ensureCourseChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(COURSE) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                COURSE,
                context.getString(R.string.item_course_reminder),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun ensurePermissionChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(PERMISSION_NOTICE) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                PERMISSION_NOTICE,
                context.getString(R.string.notification_channel_permission_notice),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun ensureMorningAlarmChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(MORNING_ALARM_FALLBACK) != null) return
        // 仅降级路径使用，故配闹钟铃声 + 震动（对齐系统闹钟体感）
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            MORNING_ALARM_FALLBACK,
            context.getString(R.string.notification_channel_morning_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_morning_alarm_desc)
            setShowBadge(true)
            if (alarmSound != null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(alarmSound, attrs)
            }
        }
        nm.createNotificationChannel(channel)
    }

    private fun ensureNextClassChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(NEXT_CLASS) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                NEXT_CLASS,
                context.getString(R.string.notification_channel_next_class),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_next_class_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun ensureExamCountdownChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(EXAM_COUNTDOWN) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                EXAM_COUNTDOWN,
                context.getString(R.string.notification_channel_exam_countdown),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_exam_countdown_desc)
                setShowBadge(true)
            }
        )
    }
}
