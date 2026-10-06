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
import com.shangkeschedule.service.notification.notify.NotificationChannels
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 课程闹钟与模式切换广播接收器。
 */
class CourseAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()

    companion object {
        const val NOTIFICATION_CHANNEL_ID = NotificationChannels.COURSE
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

        /**
         * 旧版闹钟通知的自动失效窗口（P2-26 / 37-7）。
         *
         * 覆盖一整节课的展示需求；到点后系统自动撤下 ongoing 通知，避免永久驻留状态栏。
         * 新管线按课程 startTime 精算，本旧入口只有字符串字段，故用固定窗口。
         */
        private const val LEGACY_ALARM_NOTIFICATION_TIMEOUT_MILLIS = 90L * 60L * 1000L

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
            when (modeType) {
                AutoControlMode.DND -> {
                    // FIX: 勿扰切换才需要「勿扰访问」权限；缺失时提示用户而非静默 return
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        Log.w(TAG, "勿扰访问权限未授予，已提示用户")
                        PermissionNoticeNotifier.notifyDndMissing(context)
                        return
                    }
                    notificationManager.setInterruptionFilter(
                        if (enableMode) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
                AutoControlMode.SILENT -> {
                    // FIX: 铃声模式切换不需要「勿扰访问」权限，原先被上面的权限判断一并拦截，
                    // 导致未授权用户的「上课静音」静默失效。个别 OEM 可能仍抛 SecurityException。
                    try {
                        audioManager.ringerMode = if (enableMode) AudioManager.RINGER_MODE_SILENT
                        else AudioManager.RINGER_MODE_NORMAL
                    } catch (e: SecurityException) {
                        Log.w(TAG, "静音模式切换被系统拒绝", e)
                    }
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
                                // 旧实现此处调用 DndSchedulerWorker.enqueueWork(ctx)；
                                // 该 Worker 已并入 NotificationSyncWorker（统一排程入口）。
                                // 本接收器仅作为升级过渡期兵底，新排程由 NotificationScheduler 负责。
                                androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork(
                                    com.shangkeschedule.service.notification.schedule.NotificationSyncWorker.UNIQUE_WORK_NAME,
                                    androidx.work.ExistingWorkPolicy.REPLACE,
                                    androidx.work.OneTimeWorkRequestBuilder<com.shangkeschedule.service.notification.schedule.NotificationSyncWorker>().build()
                                )
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

        // 渠道 ID 取自 NotificationChannels.COURSE（单一来源）；这里的幂等创建是防御性的：
        // 广播可能在被杀进程后由系统冷启动，不能假定 NotificationChannels.ensureAll() 已经跑过。
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

            // 升级过渡期兜底（P2-26 / 挂起清单 37-7）：非兼容模式下 ongoing=true 且 autoCancel=false，
            // 若没有任何回收路径，这条提醒会**永久**钉在状态栏、用户划不掉。
            // 新管线 CourseReminderNotifier.applyWearableCompatSemantics 已用 setTimeoutAfter 修正；
            // 本接收器是升级过渡期仍在生效的旧入口，故补同一套语义：上课时刻到达后自动消失。
            .applyCompatTimeout(isCompatMode)

            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .addAction(0, closeActionText, dismissPI)
            .setContentIntent(
                // 「打开应用」的 PendingIntent 身份是 (requestCode = 0, filterEquals)，而
                // filterEquals 不比较 Intent.flags —— 这与 WidgetRemoteViews.bindWidgetClickIntent
                // 和 DynamicIslandService 的「打开应用」共用同一个身份；FLAG_UPDATE_CURRENT 只替换
                // extras，所以三处必须写出完全相同的 Intent.flags，否则谁先创建谁生效、后创建者的
                // flags 被静默丢弃。此处与另两处保持一致（缺 NEW_TASK 时非 Activity 上下文发起会被平台拒绝）。
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
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

    /**
     * 升级过渡期兜底（P2-26 / 挂起清单 37-7）：给通知设一个自动失效期限。
     *
     * 本接收器由旧版 AlarmManager 排程触发，**触发时刻即「上课提醒时刻」**，因此从投递时刻
     * 起算一个有限窗口即可覆盖整节课的展示需求，之后由系统自动撤下，避免 `ongoing=true`
     * 且无回收路径导致通知永久钉在状态栏。
     *
     * 新管线由 CourseReminderNotifier 按课程 startTime 精确计算；本处只有字符串字段、
     * 拿不到结构化时刻，故退化为固定窗口常量，属有意的保守选择。
     */
    private fun NotificationCompat.Builder.applyCompatTimeout(@Suppress("UNUSED_PARAMETER") isCompatMode: Boolean):
        NotificationCompat.Builder = setTimeoutAfter(LEGACY_ALARM_NOTIFICATION_TIMEOUT_MILLIS)

    private fun removeAlarmIdFromPrefs(context: Context, courseId: String) {
        val sp = context.getSharedPreferences(ALARM_IDS_PREFS, Context.MODE_PRIVATE)
        val currentIds = sp.getStringSet(KEY_ACTIVE_ALARM_IDS, null)?.toMutableSet()
        if (currentIds != null) {
            currentIds.remove(courseId)
            sp.edit { putStringSet(KEY_ACTIVE_ALARM_IDS, currentIds) }
        }
    }
}