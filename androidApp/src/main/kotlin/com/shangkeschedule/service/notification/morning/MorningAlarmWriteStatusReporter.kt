package com.shangkeschedule.service.notification.morning

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
import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore
import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Kind
import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Status
import com.shangkeschedule.service.notification.notify.NotificationChannels
import kotlinx.datetime.LocalDateTime

/** 一次性闹钟状态，与每周重复闹钟核对通知相互独立。须在本次排程仍有效时调用。 */
object MorningAlarmWriteStatusReporter {
    private const val NOTIFICATION_ID = 700_102
    private const val TAG = "MorningAlarmWriteStatus"

    fun report(context: Context, result: MorningAlarmWriter.Result, plannedAt: LocalDateTime?): Boolean {
        val store = MorningAlarmWriteStatusStore(context)
        val old = store.read()
        val status = MorningAlarmWriteStatusDecision.evaluate(result, old, plannedAt)
        // 保留同一条已显示的待处理状态，避免每次后台同步反复提醒。
        if (old == status && old?.kind != Kind.UNKNOWN) return true
        val saved = if (status == null) store.clear() else store.save(status)
        if (!saved) Log.w(TAG, "无法保存早八闹钟状态")
        if (status == null && saved) clearNotification(context) else postNotification(context, status ?: Status(Kind.UNKNOWN, plannedAt))
        return saved
    }

    private fun postNotification(context: Context, status: Status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            NotificationChannels.ensureAll(context)
            val message = context.getString(when (status.kind) {
                Kind.BLOCKED -> R.string.morning_alarm_write_blocked
                Kind.DEFERRED -> R.string.morning_alarm_write_deferred
                Kind.NOT_EXPRESSIBLE -> R.string.morning_alarm_write_not_expressible
                Kind.WRITE_FAILED -> R.string.morning_alarm_write_failed
                Kind.NO_CLOCK -> R.string.morning_alarm_write_no_clock
                Kind.UNKNOWN -> R.string.morning_alarm_write_unknown
            })
            val intent = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.PERMISSION_NOTICE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.morning_alarm_write_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "无法显示早八闹钟状态通知", it) }
    }

    private fun clearNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
