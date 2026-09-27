package com.shangkeschedule.service.notification

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

    /** 确保所有渠道存在；可重复调用（已存在则跳过，不会覆盖用户设置）。 */
    fun ensureAll(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        ensureCourseChannel(context, nm)
        ensurePermissionChannel(context, nm)
        ensureMorningAlarmChannel(context, nm)
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
}
