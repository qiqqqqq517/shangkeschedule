package com.shangkeschedule.service.notification.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService
import com.shangkeschedule.R

/**
 * 通知渠道统一定义与创建。
 *
 * **渠道 ID 的唯一来源**：全部渠道 ID 只在本文件定义，其它模块一律引用这里的常量，
 * 不得再写字面量——ID 一旦分裂，就等于把用户的渠道自定义（静音、置顶、重要性）重置。
 *
 * 渠道创建的主入口是 `ensureAll()`；以下三处例外各自保留一次幂等创建（属性与本文件完全一致），
 * 因为它们的调用路径可能在进程冷启动时先于 `ensureAll()` 发通知：
 *  - `CourseAlarmReceiver`（闹钟广播，被系统冷启动拉起）；
 *  - `PermissionNoticeNotifier`（WorkManager 周期任务）；
 *  - `DynamicIslandService`（前台服务随显示窗口启停，见 DYNAMIC_ISLAND 的说明）。
 *
 * **渠道 ID 全部保持与旧版一致**：Android 上渠道的重要性/声音等属性由用户掌控，
 * 一旦换个新 ID 就等于把用户的渠道自定义（静音、置顶、重要性）全部重置。
 * 因此重写只改代码结构，不动已发布渠道的身份（唯一例外是 DYNAMIC_ISLAND 的 v1→v2，
 * 那次换 ID 是刻意为之，原因见该常量的说明）。
 */
object NotificationChannels {

    /** 课程提醒渠道（旧 ID，保持不变；`CourseAlarmReceiver` 与本文件各幂等创建一次）。 */
    const val COURSE = "course_notification_channel"

    /** 权限缺失提示渠道（旧 ID，保持不变；`PermissionNoticeNotifier` 与本文件各幂等创建一次）。 */
    const val PERMISSION_NOTICE = "permission_notice_channel"

    /**
     * 灵动岛前台服务渠道（ID 自灵动岛上线起保持不变；后缀 v2 是因为 v1 用 IMPORTANCE_LOW
     * 创建后无法升级，只能换新 ID——这是一次有意为之的身份重置，不是笔误）。
     * 创建仍留在 `DynamicIslandService`（服务随显示窗口启停），ID 引用本常量。
     */
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

    /**
     * AOSP 实况通知（Live Updates）承载渠道（新增，XL-012）。
     *
     * 实况通知的「实时活动」容器由系统提供，但通知本身仍走普通渠道，因此这里只需要
     * 一个可被提升（promote）为实况通知的渠道：IMPORTANCE_HIGH + 允许打断 +
     * 不显示角标（角标由实况形态自己表达）。
     *
     * **当前尚未触发实况提升**：把普通通知提升为 Live Updates 依赖 Android 17 的
     * `NotificationManager.canUseLiveUpdate` / 实况提升 API，本项目 compileSdk 36
     * 拿不到这些符号。此处先把渠道建好，等升到 SDK 37 再接提升逻辑 —— 届时只需在
     * 通知构建处补一级形态判断（优化清单 XL-012），不必改动渠道定义与已发布渠道的身份。
     */
    const val LIVE_UPDATE = "live_update_channel"

    /**
     * vivo 原子通知承载渠道（新增，XL-012）。
     *
     * 与实况通知同理：先建渠道，使通知在不支持原子通知的设备上也能正常降级为普通通知，
     * 而不是直接不发。
     *
     * **当前尚未启用原子形态**：vivo 的「原子通知」需要厂商私有权限与 SDK 集成
     * （并需在 vivo 开放平台申请场景），无公开文档可依据，不做猜测实现。
     * 接入方式见优化清单 XL-012 的后续项。
     */
    const val VIVO_ATOMIC = "vivo_atomic_notification_channel"

    /** 确保所有渠道存在；可重复调用（已存在则跳过，不会覆盖用户设置）。 */
    fun ensureAll(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        ensureCourseChannel(context, nm)
        ensurePermissionChannel(context, nm)
        ensureMorningAlarmChannel(context, nm)
        ensureNextClassChannel(context, nm)
        ensureExamCountdownChannel(context, nm)
        ensureLiveUpdateChannel(context, nm)
        ensureVivoAtomicChannel(context, nm)
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

    private fun ensureLiveUpdateChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(LIVE_UPDATE) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                LIVE_UPDATE,
                context.getString(R.string.notification_channel_live_update),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_live_update_desc)
                // 实况形态自己表达进度，角标会重复
                setShowBadge(false)
            }
        )
    }

    private fun ensureVivoAtomicChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(VIVO_ATOMIC) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                VIVO_ATOMIC,
                context.getString(R.string.notification_channel_vivo_atomic),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_vivo_atomic_desc)
                setShowBadge(false)
            }
        )
    }
}
