package com.shangkeschedule.service.notification.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shangkeschedule.notification.identity.AlarmCodeBook
import com.shangkeschedule.service.PermissionNoticeNotifier
import com.shangkeschedule.service.notification.receiver.AutoModeAlarmReceiver
import com.shangkeschedule.service.notification.receiver.ReminderAlarmReceiver
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * **系统闹钟层的唯一出口**：请求码分配 + 精确闹钟的挂载与注销。
 *
 * 编排层（`NotificationScheduler`）只表达「把哪个 Intent 挂在哪个时刻」，
 * 不直接接触 [AlarmManager] / [PendingIntent]，也不关心槽位编号规则。
 *
 * ## 职责边界
 *  - **本类**：请求码命名空间、`setExactAndAllowWhileIdle`、精确闹钟权限降级与提示、全量注销；
 *  - `NotificationScheduler`：算时刻、构造业务 Intent、决定排什么。
 *
 * ## 为什么请求码要分命名空间
 * 旧版课程提醒占用 50010–50110、自动勿扰占用 50001/50002，两套取消逻辑混在一起，
 * 且靠 `EXTRA_DND_ACTION` extras 区分语义，极易误取消。现在三个用途各占独立基址，
 * 一次扫完自己的区间即可，互不干扰；基址全部封在本类内部，编排层不再出现魔法数字。
 *
 * ## 精确闹钟权限
 * Android 12+ 缺 `SCHEDULE_EXACT_ALARM` 时**不静默放弃**，而是降级为
 * `setAndAllowWhileIdle`（仍能触发，只是不精确）并弹提示引导用户开启，
 * 避免「排了但永远不响」这种最难排查的失败。
 */
internal class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val codeBook = AlarmCodeBook(base = ALARM_CODE_BASE, capacity = ALARM_SLOT_LIMIT)

    /**
     * 本轮「精确闹钟权限缺失」提示是否已发过。
     *
     * v4.64.21：一轮重排最多挂 261 个闹钟（200 课程 + 60 自动模式 + 1 早八），
     * 缺权限时每个都会调一次 `notifyExactAlarmMissing`，而它内部要做
     * `resolveActivity`（PackageManager 查询）+ 渠道查询 + build + binder notify。
     * 用户侧不会看到 261 条通知（同 ID 覆盖 + `setOnlyAlertOnce`），但这是纯浪费的
     * 几百次 binder 往返。由 [cancelAll]（每轮起点）复位，保证「一轮至多一条」。
     */
    private var exactAlarmNoticeSentThisRound = false

    /** 本轮已占用的课程提醒槽位。 */
    val usedSlots: Int get() = codeBook.size

    /** 课程提醒槽位上限。 */
    val totalSlots: Int get() = codeBook.maxCodes

    /** 自动模式槽位上限（编排层用它裁剪本轮的切换序列）。 */
    val autoModeSlotLimit: Int get() = AUTO_MODE_SLOT_LIMIT

    /** 取某个 occurrence 本轮的请求码；超出容量返回 null。 */
    fun codeFor(key: String): Int? = codeBook.codeFor(key)

    /** 挂一个课程提醒闹钟（请求码由 [codeFor] 顺序分配）。 */
    fun setExact(intent: Intent, requestCode: Int, triggerAt: LocalDateTime) {
        val am = alarmManager ?: return
        val triggerMillis = triggerAt
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 缺精确闹钟权限：降级为非精确（仍可触发）+ 提示用户开启，而不是静默放弃。
            // 一轮内只提示一次（见 exactAlarmNoticeSentThisRound）。
            if (!exactAlarmNoticeSentThisRound) {
                exactAlarmNoticeSentThisRound = true
                PermissionNoticeNotifier.notifyExactAlarmMissing(context)
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            return
        }
        // 权限齐备：撤掉历史遗留的权限提示（clear 幂等，保留逐次调用不增加成本）
        PermissionNoticeNotifier.clear(context, PermissionNoticeNotifier.NOTICE_ID_EXACT_ALARM)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
    }

    /** 挂一个自动模式切换闹钟（[index] 为序列下标，基址由本类持有）。 */
    fun setAutoMode(intent: Intent, index: Int, triggerAt: LocalDateTime) {
        setExact(intent, AUTO_MODE_CODE_BASE + index, triggerAt)
    }

    /** 挂早八降级闹钟（单一请求码）。 */
    fun setMorningFallback(intent: Intent, triggerAt: LocalDateTime) {
        setExact(intent, MORNING_FALLBACK_CODE, triggerAt)
    }

    /**
     * 注销本应用排的**全部**闹钟，并重置槽位分配。
     *
     * 每次重排前调用，保证「无残留、无碰撞」——旧实现按业务分支零散取消，
     * 漏掉的槽位会带着上一次的 Intent 继续触发。
     */
    fun cancelAll() {
        val am = alarmManager ?: return
        // 每轮起点：重置「本轮已提示过精确闹钟权限缺失」，下一轮仍会正常提示一次。
        exactAlarmNoticeSentThisRound = false
        codeBook.reset()
        for (offset in 0 until ALARM_SLOT_LIMIT) {
            cancelByCode(am, applicationCancelIntent(), ALARM_CODE_BASE + offset)
        }
        for (offset in 0 until AUTO_MODE_SLOT_LIMIT) {
            cancelByCode(am, autoModeCancelIntent(), AUTO_MODE_CODE_BASE + offset)
            // PendingIntent.filterEquals 只比较 action（不含 extras），而上课/下课是
            // 同一个 requestCode 槽位上两个不同 action —— 只取消 START 会让上一次
            // 排程的「下课」闹钟残留，新排程中途把勿扰提前关掉（卡在静音态）。
            cancelByCode(am, autoModeEndCancelIntent(), AUTO_MODE_CODE_BASE + offset)
        }
        // 早八降级闹钟（系统无时钟应用时使用）
        cancelByCode(am, morningFallbackCancelIntent(), MORNING_FALLBACK_CODE)
    }

    private fun applicationCancelIntent(): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_COURSE_REMINDER
        }

    private fun autoModeCancelIntent(): Intent =
        Intent(context, AutoModeAlarmReceiver::class.java).apply {
            action = AutoModeAlarmReceiver.ACTION_AUTO_MODE_START
        }

    private fun autoModeEndCancelIntent(): Intent =
        Intent(context, AutoModeAlarmReceiver::class.java).apply {
            action = AutoModeAlarmReceiver.ACTION_AUTO_MODE_END
        }

    private fun morningFallbackCancelIntent(): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_MORNING_ALARM_FALLBACK
        }

    private fun cancelByCode(am: AlarmManager, intent: Intent, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private companion object {
        /** 提醒闹钟请求码基址（与旧 50010 区间完全隔离）。 */
        const val ALARM_CODE_BASE = 61_000

        /** 提醒闹钟槽位上限（覆盖 7 天 × 每天 20+ 节的极端课表）。 */
        const val ALARM_SLOT_LIMIT = 200

        /** 自动模式请求码基址与上限（独立命名空间，不再与课程提醒挤在一起）。 */
        const val AUTO_MODE_CODE_BASE = 63_000
        const val AUTO_MODE_SLOT_LIMIT = 60

        /** 早八降级闹钟请求码（单一，只排最近一条）。 */
        const val MORNING_FALLBACK_CODE = 65_000
    }
}
