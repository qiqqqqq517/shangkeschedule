package com.shangkeschedule.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.shangkeschedule.data.repository.AppSettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 课程闹钟与模式切换广播接收器。
 */
class CourseAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "course_notification_channel"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_COURSE_POSITION = "course_position"
        const val EXTRA_COURSE_TEACHER = "extra_course_teacher"
        const val EXTRA_COURSE_ID = "course_id"

        const val EXTRA_DND_ACTION = "extra_dnd_action"
        const val DND_ACTION_START = "dnd_action_start"
        const val DND_ACTION_END = "dnd_action_end"

        const val EXTRA_ALARM_SLOT_ID = "EXTRA_ALARM_SLOT_ID"
        // 覆盖 50001 (DND) 到 50110 (Course Reminders)
        private const val SLOT_START = 50001
        private const val SLOT_END = 50110

        const val ACTION_DISMISS_NOTIFICATION = "com.shangkeschedule.ACTION_DISMISS_NOTIFICATION"

        private const val ALARM_IDS_PREFS = "alarm_ids_prefs"
        private const val KEY_ACTIVE_ALARM_IDS = "active_alarm_ids"
        private const val TAG = "CourseAlarmReceiver"
        private const val LAUNCHER_ICON_SIZE_PX = 192

        /** 回退图圆角比例（相对半边长）：方图圆角 ≈ 22.5%，与系统自适应图标观感接近。 */
        private const val LAUNCHER_ICON_CORNER_RATIO = 0.45f

        /** 回退图前景安全区比例：自适应图标前景在 108dp 画布中约 72dp 可见（66.7%）。 */
        private const val LAUNCHER_ICON_SAFE_ZONE_RATIO = 0.72f

        /**
         * 通知大图标位图（应用图标）。
         *
         * 主路径：ContextCompat 把 R.mipmap.ic_launcher 取成 AdaptiveIconDrawable（minSdk 26
         * 下该资源只解析到 mipmap-anydpi-v26/ 的 `<adaptive-icon>` XML），setBounds + draw
         * 光栅化到方画布——由系统蒙版裁出圆角/圆形与背景层，与 API 版本无关。
         * （历史写法用 BitmapFactory.decodeResource 直接解该 XML，必然返回 null ⇒
         * 通知大图标恒为空；此为修复点。）
         *
         * 回退（仅防御：drawable 取不到或 draw 抛异常）：品牌背景色圆角底 + 前景层 webp
         * 合成，避免直接贴全出血前景图产生方形硬边。仍失败则返回 null——
         * 通知不显示大图标，但不阻断提醒本身。
         */
        private fun launcherIconBitmap(context: Context): Bitmap? = try {
            val drawable: android.graphics.drawable.Drawable? =
                androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val size = LAUNCHER_ICON_SIZE_PX
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(bitmap))
                    bitmap
                } catch (e: Exception) {
                    // 绘制中途失败：回收半成品，交给回退合成
                    bitmap.recycle()
                    throw e
                }
            } else {
                fallbackLauncherIcon(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "通知大图标光栅化失败，改用背景色+前景合成", e)
            fallbackLauncherIcon(context)
        }

        /** 回退合成：品牌背景色圆角底 + 前景层（前景为全出血 webp，直接贴会有方形硬边）。 */
        private fun fallbackLauncherIcon(context: Context): Bitmap? {
            val foreground = try {
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            } catch (e: Exception) {
                null
            } ?: return null
            val size = LAUNCHER_ICON_SIZE_PX
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                val radius = size / 2f * LAUNCHER_ICON_CORNER_RATIO
                val bgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = androidx.core.content.ContextCompat.getColor(context, R.color.ic_launcher_background)
                }
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bgPaint)
                val iconPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                // 前景按安全区缩放（0.72：自适应图标前景在 108dp 画布内的可见区比例）
                val inset = size * (1f - LAUNCHER_ICON_SAFE_ZONE_RATIO) / 2f
                canvas.drawBitmap(
                    foreground,
                    null,
                    android.graphics.RectF(inset, inset, size - inset, size - inset),
                    iconPaint
                )
                return bitmap
            } catch (e: Exception) {
                // 合成失败：回收半成品，返回 null（通知不显示大图标）
                bitmap.recycle()
                Log.w(TAG, "通知大图标回退合成失败", e)
                return null
            } finally {
                foreground.recycle()
            }
        }

        fun toggleMode(context: Context, enableMode: Boolean, modeType: AutoControlMode) {
            val audioManager = context.getSystemService<AudioManager>()
            val notificationManager = context.getSystemService<NotificationManager>()
            if (audioManager == null || notificationManager == null) return
            if (!notificationManager.isNotificationPolicyAccessGranted) return
            when (modeType) {
                AutoControlMode.DND -> {
                    notificationManager.setInterruptionFilter(
                        if (enableMode) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
                AutoControlMode.SILENT -> {
                    audioManager.ringerMode = if (enableMode) AudioManager.RINGER_MODE_SILENT
                    else AudioManager.RINGER_MODE_NORMAL
                }
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { ctx ->
            if (intent?.action == ACTION_DISMISS_NOTIFICATION) {
                val notifyId = intent.getIntExtra("target_notification_id", -1)
                if (notifyId != -1) {
                    val nm = ctx.getSystemService<NotificationManager>()
                    nm?.cancel(notifyId)
                }
                return
            }

            val slotId = intent?.getIntExtra(EXTRA_ALARM_SLOT_ID, -1) ?: -1
            val dndAction = intent?.getStringExtra(EXTRA_DND_ACTION)

            if (intent?.data != null || (slotId !in SLOT_START..SLOT_END && dndAction.isNullOrEmpty())) {
                Log.d(TAG, "已拦截非法或旧版闹钟。")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appSettings = appSettingsRepository.getAppSettingsOnce()
                    val modeToUse = appSettings.autoControlMode
                    val isCompatMode = appSettings.compatWearableSync

                    if (!dndAction.isNullOrEmpty()) {
                        when (dndAction) {
                            DND_ACTION_START -> toggleMode(ctx, true, modeToUse)
                            DND_ACTION_END -> {
                                toggleMode(ctx, false, modeToUse)
                                DndSchedulerWorker.enqueueWork(ctx)
                            }
                        }
                    } else {
                        val courseName = intent?.getStringExtra(EXTRA_COURSE_NAME).takeUnless { it.isNullOrEmpty() }
                            ?: ctx.getString(R.string.notification_unknown_course)

                        val position = intent?.getStringExtra(EXTRA_COURSE_POSITION).takeUnless { it.isNullOrEmpty() }
                            ?: ctx.getString(R.string.notification_unknown_position)
                        val teacher = intent?.getStringExtra(EXTRA_COURSE_TEACHER) ?: ""
                        val courseIdString = intent?.getStringExtra(EXTRA_COURSE_ID)

                        if (!courseIdString.isNullOrEmpty()) {
                            showNotification(ctx, slotId, courseName, position, teacher, isCompatMode)
                            removeAlarmIdFromPrefs(ctx, courseIdString)
                            updateAllWidgets(ctx)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onReceive 异常", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        name: String,
        position: String,
        teacher: String,
        isCompatMode: Boolean
    ) {
        val nm = context.getSystemService<NotificationManager>() ?: return

        val alertTitle = context.getString(R.string.notification_title_course_alert)
        val posLabel = context.getString(R.string.label_position)
        val teacherLabel = context.getString(R.string.label_teacher)
        val closeActionText = context.getString(R.string.action_close)
        val liveStatusText = context.getString(R.string.notification_live_status_preparing)

        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.item_course_reminder),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(context, CourseAlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
            putExtra("target_notification_id", notificationId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                identifier = notificationId.toString()
            }
        }
        val dismissPI = PendingIntent.getBroadcast(
            context, notificationId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(name)
            .bigText("$posLabel: $position\n$teacherLabel: $teacher")

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(launcherIconBitmap(context))
            .setContentTitle(name)
            .setContentText("$posLabel: $position")
            .setSubText(alertTitle)
            .setStyle(bigTextStyle)

            // 非兼容模式应用
            .setOngoing(!isCompatMode)
            .setAutoCancel(isCompatMode)

            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .addAction(0, closeActionText, dismissPI)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

        // Android 16 实时更新特性
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(liveStatusText)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun removeAlarmIdFromPrefs(context: Context, courseId: String) {
        val sp = context.getSharedPreferences(ALARM_IDS_PREFS, Context.MODE_PRIVATE)
        val currentIds = sp.getStringSet(KEY_ACTIVE_ALARM_IDS, null)?.toMutableSet()
        if (currentIds != null) {
            currentIds.remove(courseId)
            sp.edit { putStringSet(KEY_ACTIVE_ALARM_IDS, currentIds) }
        }
    }
}