package com.shangkeschedule.service.notification.morning

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.service.notification.notify.NotificationChannels

/** 只提示人工核对，不使用闹钟响铃渠道；持久状态由 WeeklyAlarmConfirmationStore 管理。 */
internal object MorningAlarmReviewNotifier {
    private const val ID = 700_101

    fun post(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            NotificationChannels.ensureAll(context)
            val intent = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val message = context.getString(R.string.morning_alarm_review_notification_text)
            val notice = NotificationCompat.Builder(context, NotificationChannels.PERMISSION_NOTICE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.morning_alarm_review_notification_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .build()
            NotificationManagerCompat.from(context).notify(ID, notice)
        }.onFailure { Log.w("MorningAlarmReview", "早八闹钟核对通知发送失败", it) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }
}
