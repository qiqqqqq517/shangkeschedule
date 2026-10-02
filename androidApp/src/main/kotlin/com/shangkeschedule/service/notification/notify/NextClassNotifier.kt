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
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.notification.identity.NotificationIds
import com.shangkeschedule.notification.live.LiveUpdateSupport
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.receiver.NextClassControlReceiver
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 「下一节课」常驻通知（补全方案 §G1）。
 *
 * 与「课程提醒」([CourseReminderNotifier]，到点响一声) 的分工：
 * 本通知**一直挂在状态栏**，抬眼就能看到「接下来上什么、还有多久、在哪」。
 *
 * ## 四条硬约束
 *  1. **能被彻底撤下**：`setOngoing(true)` 的通知用户划不掉，所以必须给退路 ——
 *     通知内的「关闭常驻」动作（[NextClassControlReceiver]）与设置页开关，任一都能
 *     立即取消通知并注销周期刷新任务；无课（今天与明天都没课）时主动
 *     [cancel]，不留空壳。
 *  2. **可自愈**：每次 [sync] 都从数据源整算一遍（不做增量），因此进程重启、
 *     跨零点、改时区都会在下一轮自动收敛，不会卡在过期内容上。
 *  3. **节假日避让**：课程集合由调用方用 [ReminderEngine.effectiveCourses] 预处理，
 *     本类不二次过滤 —— 避免出现「各处避让规则不一致」。
 *  4. **不响不震不弹**：渠道 IMPORTANCE_LOW + `setSilent(true)`，它只是块状态牌；
 *     且**不注册进** `PostedNotificationRegistry` —— 那是课程提醒的回收账本，
 *     `CourseReminderScheduler` 的 `pruneExcept` 会把它人的条目一并取消。
 */
class NextClassNotifier(private val context: Context) {

    /**
     * 整算并投递（或撤下）常驻通知。
     *
     * @param courses 已按 [ReminderEngine.effectiveCourses] 处理过的课程（含未来 7 天）
     * @param today 当前本地日期
     * @param nowMinutes 当前本地时刻（0..1439）
     * @param requestChip 是否允许声明「状态栏胶囊」（Android 16+ 实况更新，§G2）。
     *   与灵动岛同源：用户打开「兼容穿戴设备同步通知」时不再抢状态栏胶囊位。
     *   不支持的机型上是空操作，通知照常投递。
     * @return 是否留下了通知（false = 已 [cancel]）
     */
    fun sync(
        courses: List<WidgetCourse>,
        today: LocalDate,
        nowMinutes: Int,
        requestChip: Boolean = true
    ): Boolean {
        val todayStr = today.toString()
        val tomorrowStr = today.plus(1, DateTimeUnit.DAY).toString()

        // 今天还剩的课：结束时间晚于此刻的第一节。
        // 用「结束时间」而不是「开始时间」判定，是为了让正在上的那节课继续挂在通知里
        // （显示「正在上课」），而不是一上课就跳到下一节。
        val nextToday = courses
            .asSequence()
            .filter { it.date == todayStr }
            .sortedBy { minutesOf(it.startTime) ?: Int.MAX_VALUE }
            .firstOrNull { (minutesOf(it.endTime) ?: Int.MIN_VALUE) > nowMinutes }

        if (nextToday != null) {
            post(
                titleRes = R.string.notification_next_class_title,
                course = nextToday,
                detail = countdownText(nextToday, nowMinutes),
                chipText = chipTextFor(nextToday, nowMinutes),
                requestChip = requestChip
            )
            return true
        }

        // 今天没课或已上完 → 退一步显示「明日第一节」（跨零点后自然变成「下一节课」）。
        val firstTomorrow = courses
            .asSequence()
            .filter { it.date == tomorrowStr }
            .minByOrNull { minutesOf(it.startTime) ?: Int.MAX_VALUE }
        if (firstTomorrow != null) {
            post(
                titleRes = R.string.notification_next_class_tomorrow,
                course = firstTomorrow,
                detail = firstTomorrow.startTime,
                chipText = context.getString(
                    R.string.notification_next_class_chip_at,
                    firstTomorrow.startTime.take(5),
                    firstTomorrow.name
                ),
                requestChip = requestChip
            )
            return true
        }

        cancel()
        return false
    }

    /** 撤下常驻通知（关闭开关 / 通知内动作 / 无课都走这里）。 */
    fun cancel() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NotificationIds.NEXT_CLASS_PERSISTENT_ID)
        }.onFailure { Log.w(TAG, "取消「下一节课」常驻通知失败: ${it.message}") }
    }

    /**
     * 投递常驻通知。
     *
     * @param chipText 状态栏胶囊里的短文本（§G2）——系统只在实况更新里用它，
     *   普通通知栏不显示；因此它必须**自带语境**（含课名，不能只写「3 分钟后」）。
     */
    private fun post(
        titleRes: Int,
        course: WidgetCourse,
        detail: String,
        chipText: String,
        requestChip: Boolean
    ) {
        if (!hasPermission()) {
            Log.w(TAG, "通知权限未授予，「下一节课」常驻通知无法投递")
            return
        }
        runCatching {
            NotificationChannels.ensureAll(context)
            val text = listOf(course.name, course.position, course.teacher, detail)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            val builder = NotificationCompat.Builder(context, NotificationChannels.NEXT_CLASS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(titleRes))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .addAction(
                    0,
                    context.getString(R.string.notification_next_class_disable),
                    disableIntent()
                )
            if (requestChip) applyChip(builder, chipText)
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.NEXT_CLASS_PERSISTENT_ID, builder.build())
        }.onFailure { Log.e(TAG, "投递「下一节课」常驻通知失败", it) }
    }

    /**
     * 声明「状态栏胶囊」（补全方案 §G2）。
     *
     * 平台 Builder 没有这两个方法，跨版本调用会 NoSuchMethodError（真机踩过），
     * 所以必须先用 [LiveUpdateSupport] 探测；不满足时**什么都不做**，
     * 通知照常是普通常驻通知 —— 这正是「优雅降级」的落点。
     */
    private fun applyChip(builder: NotificationCompat.Builder, chipText: String) {
        if (!LiveUpdateSupport.supportsLiveUpdate(context)) return
        runCatching {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(chipText)
        }.onFailure { Log.w(TAG, "声明状态栏胶囊失败，按普通常驻通知投递: ${it.message}") }
    }

    /** 胶囊短文本：正在上课 / 还有 X 分钟 / 兜底只给开始时间（§G2）。 */
    private fun chipTextFor(course: WidgetCourse, nowMinutes: Int): String {
        val start = minutesOf(course.startTime)
        val end = minutesOf(course.endTime)
        return when {
            start != null && end != null && nowMinutes >= start && nowMinutes < end ->
                context.getString(R.string.notification_next_class_chip_ongoing, course.name)

            start != null && start > nowMinutes ->
                context.getString(
                    R.string.notification_next_class_chip_in_minutes,
                    start - nowMinutes,
                    course.name
                )

            else -> context.getString(
                R.string.notification_next_class_chip_at,
                course.startTime.take(5),
                course.name
            )
        }
    }

    /** 剩余分钟 / 正在上课 / 兜底只显示开始时间。 */
    private fun countdownText(course: WidgetCourse, nowMinutes: Int): String {
        val start = minutesOf(course.startTime) ?: return course.startTime
        val end = minutesOf(course.endTime)
        return when {
            end != null && nowMinutes >= start && nowMinutes < end ->
                context.getString(R.string.notification_next_class_ongoing)

            start > nowMinutes ->
                context.getString(R.string.notification_next_class_in_minutes, start - nowMinutes)

            else -> course.startTime
        }
    }

    private fun minutesOf(value: String): Int? =
        ReminderEngine.parseTime(value)?.let { it.hour * 60 + it.minute }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.NEXT_CLASS_PERSISTENT_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun disableIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        NotificationIds.NEXT_CLASS_PERSISTENT_ID,
        Intent(context, NextClassControlReceiver::class.java).setAction(NextClassControlReceiver.ACTION_DISABLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "NextClassNotifier"
    }
}
