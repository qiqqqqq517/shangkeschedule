package com.shangkeschedule.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.shangkeschedule.R
import com.shangkeschedule.ui.settings.notification.hasNotificationPermission

/**
 * 权限缺失提示通知。
 *
 * 背景：精确闹钟权限（Android 12+）与勿扰访问权限被系统回收后，
 * 课程提醒 / 上课自动勿扰只会在日志里留下一条 Log.w，用户侧完全无感知
 * ——功能静默失效，用户既不知道失效原因，也不知道去哪里恢复。
 * 这里补一条可点击直达系统设置页的提示通知，让失效「可见、可恢复」。
 *
 * 实现要点：
 * - 固定通知 ID + [NotificationCompat.Builder.setOnlyAlertOnce]：WorkManager 周期任务
 *   重复调用只会更新同一条通知，不会刷屏；权限恢复后由调用方调用 [clear] 主动清除。
 * - 通知权限本身缺失时无法提示，直接静默返回（此时系统也不会展示任何通知）。
 * - 全流程 try/catch：提示失败绝不能影响 Worker / BroadcastReceiver 的主流程。
 */
object PermissionNoticeNotifier {

    private const val TAG = "PermissionNotice"
    private const val CHANNEL_ID = "permission_notice_channel"

    /** 精确闹钟权限缺失提示的通知 ID。 */
    const val NOTICE_ID_EXACT_ALARM = 50190

    /** 勿扰访问权限缺失提示的通知 ID。 */
    const val NOTICE_ID_DND = 50191

    /** 精确闹钟权限缺失：提醒与自动勿扰无法按时触发。 */
    fun notifyExactAlarmMissing(context: Context) {
        post(
            context = context,
            notificationId = NOTICE_ID_EXACT_ALARM,
            titleRes = R.string.notification_title_exact_alarm_missing,
            textRes = R.string.notification_text_exact_alarm_missing,
            settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            } else {
                null
            }
        )
    }

    /** 勿扰访问权限缺失：上课自动勿扰/静音无法切换。 */
    fun notifyDndMissing(context: Context) {
        post(
            context = context,
            notificationId = NOTICE_ID_DND,
            titleRes = R.string.notification_title_dnd_missing,
            textRes = R.string.notification_text_dnd_missing,
            settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        )
    }

    /** 权限已恢复：清除对应提示，避免残留误导。 */
    fun clear(context: Context, notificationId: Int) {
        try {
            context.getSystemService<NotificationManager>()?.cancel(notificationId)
        } catch (e: Exception) {
            Log.w(TAG, "权限提示通知清除失败", e)
        }
    }

    private fun post(
        context: Context,
        notificationId: Int,
        titleRes: Int,
        textRes: Int,
        settingsIntent: Intent?
    ) {
        try {
            if (!hasNotificationPermission(context)) return

            val nm = context.getSystemService<NotificationManager>() ?: return
            ensureChannel(context, nm)

            val text = context.getString(textRes)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(titleRes))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(settingsPendingIntent(context, notificationId, settingsIntent))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.w(TAG, "权限提示通知发送失败", e)
        }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_permission_notice),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /**
     * 构造点击跳转：优先直达对应权限设置页，系统裁剪导致不可解析时退回应用详情页。
     */
    private fun settingsPendingIntent(
        context: Context,
        notificationId: Int,
        preferred: Intent?
    ): PendingIntent {
        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        )

        val target = (preferred ?: fallback).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        @Suppress("DEPRECATION")
        val resolvable = context.packageManager.resolveActivity(target, 0) != null
        val finalIntent = if (resolvable) target else fallback.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            context,
            notificationId,
            finalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
