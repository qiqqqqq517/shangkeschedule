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

/**
 * 早八闹钟的**应用内降级提醒**。
 *
 * 仅在系统无可用时钟应用（`packageManager.resolveActivity(ACTION_SET_ALARM)` 为空）时
 * 才会被排程并触发——否则早八闹钟一律写入系统时钟应用，由系统闹钟负责响铃，
 * 本应用不重复打扰（双重响铃是明确的错误体验）。
 */
object MorningAlarmNotifier {

    private const val TAG = "MorningAlarmNotifier"

    /** 固定通知 ID：同一时刻只会有一个早八提醒（每天一节），不需要 occurrence 区分。 */
    const val NOTIFICATION_ID = 700_100

    fun postFallback(context: Context, title: String, courseName: String, startTime: String) {
        if (!hasPermission(context)) {
            Log.w(TAG, "通知权限未授予，早八降级提醒无法投递")
            return
        }
        runCatching {
            NotificationChannels.ensureAll(context)
            val contentText = context.getString(
                R.string.notification_text_morning_alarm_format,
                startTime.ifBlank { "—" },
                courseName.ifBlank { context.getString(R.string.notification_unknown_course) }
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.MORNING_ALARM_FALLBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title.ifBlank { context.getString(R.string.notification_title_morning_alarm) })
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "投递早八降级提醒失败", it) }
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
