package com.shangkeschedule.service.notification.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.notification.identity.NotificationIds
import com.shangkeschedule.service.notification.receiver.ReminderAlarmReceiver
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 通知超时的兜底下限（R41-05）。
 *
 * 提前量 0（正点提醒）时距上课时刻的毫秒数 ≤ 0，若照旧「不设超时」，
 * ongoing 通知将没有任何回收路径，只能等下一次重排 / 开机 / 零点。
 * 给一分钟足够让过期提醒消失，又不至于长到被误认为没生效。
 */
private const val MIN_TIMEOUT_AFTER_MILLIS = 60_000L

/**
 * 课程提醒通知构建器。
 *
 * 相对旧 `CourseAlarmReceiver.showNotification` 的四处修正：
 *
 * 1. **通知 ID 用 occurrence 稳定身份**（[NotificationIds]），不再拿闹钟槽位号充当。
 *    旧实现槽位按列表序号重排，课表一变同一槽位就换了课程，导致通知互相顶替。
 *
 * 2. **「关闭」恒指向本条通知**：dismiss 的 PendingIntent requestCode 用同一个稳定 ID，
 *    并带上 `identifier`（API 29+）保证 PendingIntent 唯一。旧实现同样用槽位号，
 *    课表变动后可能把「关闭」按到别的课上。
 *
 * 3. **通知生命周期自洽**（[applyWearableCompatSemantics]）：
 *    旧实现 `setOngoing(!compat)` + `setAutoCancel(compat)`，非兼容模式下 ongoing 且
 *    **无任何回收路径**，提醒永久残留在状态栏——把「一次性提醒」错当「进行中状态」用。
 *    （课中实时状态由灵动岛前台服务负责，不该由提醒通知兼任。）
 *
 * 4. **大图标光栅化**逻辑保持既有正确实现（`ContextCompat.getDrawable` + Canvas 光栅化，
 *    而非 `BitmapFactory.decodeResource` 直接解自适应图标 XML——后者必返回 null）。
 */
class CourseReminderNotifier(private val context: Context) {

    private val registry = PostedNotificationRegistry(context)

    /**
     * 投递一条课程提醒。
     *
     * @param course 对应课程（用于取稳定通知 ID 与 occurrence 键）
     * @param compatWearableSync 兼容穿戴设备同步开关（决定通知语义，见类注释）
     * @return 投递成功返回通知 ID，通知权限缺失等情况下返回 null
     */
    fun post(course: WidgetCourse, compatWearableSync: Boolean): Int? {
        val notificationId = NotificationIds.forOccurrence(course)
        val occurrenceKey = NotificationIds.occurrenceKey(course)

        if (!hasNotificationPermission()) {
            Log.w(TAG, "通知权限未授予，跳过课程提醒投递")
            return null
        }

        return runCatching {
            NotificationChannels.ensureAll(context)
            val notification = build(course, notificationId, compatWearableSync)
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            // 登记以便后续重排/停用时回收（修复旧实现「通知永不清理」缺陷）
            registry.register(occurrenceKey, notificationId)
            notificationId
        }.onFailure { Log.e(TAG, "投递课程提醒失败", it) }.getOrNull()
    }

    private fun build(
        course: WidgetCourse,
        notificationId: Int,
        compatWearableSync: Boolean
    ): Notification {
        val alertTitle = context.getString(R.string.notification_title_course_alert)
        val posLabel = context.getString(R.string.label_position)
        val teacherLabel = context.getString(R.string.label_teacher)
        val closeActionText = context.getString(R.string.action_close)

        val position = course.position.ifBlank { context.getString(R.string.notification_unknown_position) }
        val teacher = course.teacher.ifBlank { "—" }

        val dismissPi = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_DISMISS_NOTIFICATION
                putExtra(ReminderAlarmReceiver.EXTRA_TARGET_NOTIFICATION_ID, notificationId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+：以 identifier 保证 PendingIntent 唯一，避免同 requestCode 互相覆盖
                    identifier = notificationId.toString()
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentPi = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.COURSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(launcherIconBitmap())
            .setContentTitle(course.name)
            .setContentText("$posLabel: $position")
            .setSubText(alertTitle)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(course.name)
                    .bigText("$posLabel: $position\n$teacherLabel: $teacher")
            )
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .addAction(0, closeActionText, dismissPi)
            .setContentIntent(contentPi)

        applyWearableCompatSemantics(builder, course, compatWearableSync)

        // Android 16 实时更新特性（与原实现一致）
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(context.getString(R.string.notification_live_status_preparing))
        }

        return builder.build()
    }

    /**
     * 落地「兼容穿戴设备同步通知」的双语义。
     *
     * | 模式 | ongoing | autoCancel | 超时 |
     * |---|---|---|---|
     * | 非兼容（默认） | true | false | 上课时刻 → 自动消失 |
     * | 兼容 | false | true | 上课时刻 → 兜底消失 |
     *
     * **共同点**：两者都用 [NotificationCompat.Builder.setTimeoutAfter]（API 26+，覆盖 minSdk 26）
     * 让通知在「上课时刻」自动消失。这是对旧实现的核心修正——旧版非兼容模式
     * `setOngoing(true)` 且无任何回收路径，提醒会永久饥饿在状态栏。
     *
     * **差异**：非兼容模式不许用户误划掉（ongoing），穿戴设备若走 ongoing 通知
     * 往往不会当作「告警」同步推送，故兼容模式退回普通可划掉的一次性通知。
     */
    private fun applyWearableCompatSemantics(
        builder: NotificationCompat.Builder,
        course: WidgetCourse,
        compatWearableSync: Boolean
    ) {
        val timeoutMillis = timeoutUntilClassStartMillis(course)
        // R41-05：提前量 = 0（正点提醒）时 `timeoutMillis <= 0`（触发时刻即上课时刻），
        // 旧代码于是**不调 setTimeoutAfter**。而默认非兼容模式是 setOngoing(true) +
        // setAutoCancel(false) ⇒ 通知划不掉；回收只发生在 registry.pruneExcept()，而已过期
        // occurrence 在 ReminderPlan 里被 `if (triggerAt <= now) continue` 剔除 ⇒ 要等下一次
        // 重排 / 开机 / 零点才回收。正点提醒因此从「响一声即走」退化成「状态栏长期挂着已过时的
        // 上课提醒」，恰好退回本文件 KDoc 里被明确修正掉的旧形态。
        //
        // 修法：≤ 0 时给一个**最小超时**（正点提醒立刻就会过去 ⇒ 一分钟足够回收，
        // 期间用户仍可手动划掉或点关闭），保证 non-going 与 going 两条路径都不会永久滞留。
        if (compatWearableSync) {
            builder.setOngoing(false)
            builder.setAutoCancel(true)
        } else {
            builder.setOngoing(true)
            builder.setAutoCancel(false)
        }
        when {
            timeoutMillis != null && timeoutMillis > 0 -> builder.setTimeoutAfter(timeoutMillis)
            // 上课时刻已到（或提前量为 0）⇒ 仍要设超时，否则 ongoing 通知无回收路径。
            timeoutMillis != null -> builder.setTimeoutAfter(MIN_TIMEOUT_AFTER_MILLIS)
            // 数据不可解析：退化为最小超时，避免 ongoing 通知永久滞留。
            else -> builder.setTimeoutAfter(MIN_TIMEOUT_AFTER_MILLIS)
        }
    }

    /**
     * 距「上课时刻」还有多少毫秒（用作通知超时）。
     * 上课时刻已过或数据不可解析时返回 null（不设超时，交由重排回收兜底）。
     */
    private fun timeoutUntilClassStartMillis(course: WidgetCourse): Long? {
        return runCatching {
            val start = LocalTime.parse(course.startTime)
            val date = LocalDate.parse(course.date)
            val startAt = LocalDateTime.of(date, start)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            Duration.ofMillis(startAt - System.currentTimeMillis()).toMillis()
        }.getOrNull()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------------
    // 大图标（自适应图标光栅化）
    // ---------------------------------------------------------------------

    /**
     * 通知大图标位图（应用图标）。
     *
     * 主路径：`ContextCompat.getDrawable` 取 `R.mipmap.ic_launcher`（minSdk 26 下该资源
     * 只解析到 `mipmap-anydpi-v26/` 的 `<adaptive-icon>` XML），setBounds + draw 光栅化，
     * 由系统蒙版裁出圆角。
     * （历史写法 `BitmapFactory.decodeResource` 直接解该 XML 必然返回 null ⇒ 大图标恒为空。）
     */
    private fun launcherIconBitmap(): Bitmap? = try {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        if (drawable != null) {
            val size = LAUNCHER_ICON_SIZE_PX
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
                bitmap
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            }
        } else {
            fallbackLauncherIcon()
        }
    } catch (e: Exception) {
        Log.w(TAG, "通知大图标光栅化失败，改用背景色+前景合成", e)
        fallbackLauncherIcon()
    }

    /** 回退合成：品牌背景色圆角底 + 前景层（前景为全出血图，直接贴会有方形硬边）。 */
    private fun fallbackLauncherIcon(): Bitmap? {
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
                color = ContextCompat.getColor(context, R.color.ic_launcher_background)
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
            bitmap.recycle()
            Log.w(TAG, "通知大图标回退合成失败", e)
            return null
        } finally {
            foreground.recycle()
        }
    }

    private companion object {
        const val TAG = "CourseReminderNotifier"
        const val LAUNCHER_ICON_SIZE_PX = 192
        const val CONTENT_REQUEST_CODE = 70001

        /** 回退图圆角比例（相对半边长）：方图圆角 ≈ 22.5%，与系统自适应图标观感接近。 */
        const val LAUNCHER_ICON_CORNER_RATIO = 0.45f

        /** 回退图前景安全区比例：自适应图标前景在 108dp 画布中约 72dp 可见（66.7%）。 */
        const val LAUNCHER_ICON_SAFE_ZONE_RATIO = 0.72f
    }
}
