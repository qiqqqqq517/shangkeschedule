package com.shangkeschedule.service.notification.control

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.notification.plan.AutoModePlan
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.receiver.AutoModeAlarmReceiver
import kotlinx.datetime.LocalDateTime

/**
 * 上课自动勿扰/静音**排程策略**：把「上课开始 → 开启 / 下课结束 → 关闭」挂成闹钟，并做卡态校准。
 *
 * 从 `NotificationScheduler` 按作用拆出，只负责自动模式：
 *  1. 切换序列由 [ReminderEngine.autoModeTransitions] 算出（相邻课间的「关-开」同刻对已合并）；
 *  2. 只挂**未来**的切换，槽位数受 [AlarmScheduler.autoModeSlotLimit] 限制；
 *  3. 每次重排都按 [ReminderEngine.shouldModeBeOn] 校准真实状态：下课闹钟因重启/进程被杀丢失时，
 *     模式会卡在勿扰直到下次同步——旧实现算了这个判断却只写日志，从不应用。
 *
 * 「怎么改系统状态」不在本类，而归 [AutoModeController]；「当前到底开没开」归 [AutoModeStateProbe]。
 */
internal class AutoModeScheduler(
    private val context: Context,
    private val alarms: AlarmScheduler
) {

    /**
     * 按总开关排程自动模式并校准状态。
     *
     * @param courses 已由 [ReminderEngine.effectiveCourses] 过滤过的有效课程
     * @return 本轮挂上的切换闹钟条数
     */
    fun sync(settings: AppSettingsModel, courses: List<WidgetCourse>, now: LocalDateTime): Int {
        if (!settings.autoModeEnabled) {
            // R41-02：关闭开关时必须**还原**已施加的勿扰/静音，不能只是「不再排新的」。
            //
            // 旧实现 `if (!enabled) return 0` 在第一行就返回，`reconcileState`（它之后）永不执行；
            // 而 `NotificationScheduler.reschedule()` 已在此之前跑过 `alarms.cancelAll()`，
            // 把已排的 END 闹钟全部注销 ⇒ 用户选 OFF 后设备**停留在勿扰/静音直到手动改回**，
            // `AutoModeController` 落盘的「进入前状态」成为永久孤儿记录。
            //
            // 同仓库另两套策略在关闭时都有显式收尾（早八 `markDisabled()`、课程提醒
            // `registry.clear()`），此处补齐第三条。还原动作幂等：`AutoModeController` 的
            // enable=false 分支在「本就处于正常态」时是无害写入，且成功后才清记录。
            restoreIfApplied(settings.autoControlMode)
            return 0
        }
        val scheduled = schedule(courses, now)
        reconcileState(settings, courses, now)
        return scheduled
    }

    /**
     * 开关关闭时的收尾：若当前确实处于自动模式施加的状态，就还原。
     *
     * 「是否处于自动模式施加的状态」用 [AutoModeStateProbe] 的回读判据，而不是
     * 「有没有落过记录」——后者在进入前状态恰好等于目标档（如用户自己就开着勿扰、
     * [AutoModeController] 只在「当前并非目标档」时才记 `KEY_DND_FILTER_BEFORE`）时
     * 会永不落盘，导致该还原的一次也不还原。
     */
    private fun restoreIfApplied(modeType: AutoControlMode) {
        if (!AutoModeStateProbe.isModeOn(context, modeType)) return
        Log.i(TAG, "自动模式已关闭，还原当前已施加的勿扰/静音")
        AutoModeController.toggle(context, false, modeType)
    }

    private fun schedule(courses: List<WidgetCourse>, now: LocalDateTime): Int {
        // 「丢掉过期切换 + 按槽位上限截断编号」在 AutoModePlan 里（纯逻辑，可单测）
        val entries = AutoModePlan.select(
            transitions = ReminderEngine.autoModeTransitions(courses),
            now = now,
            slotLimit = alarms.autoModeSlotLimit
        )

        for (entry in entries) {
            val action = if (entry.enable) {
                AutoModeAlarmReceiver.ACTION_AUTO_MODE_START
            } else {
                AutoModeAlarmReceiver.ACTION_AUTO_MODE_END
            }
            val intent = Intent(context, AutoModeAlarmReceiver::class.java).apply {
                this.action = action
            }
            alarms.setAutoMode(intent, entry.index, entry.triggerAt)
        }
        return entries.size
    }

    /**
     * 状态校准：若实际模式状态与「当前是否在上课」不一致，就地纠正。
     *
     * 场景：下课时刻的 END 闹钟因设备重启/进程被杀而丢失 → 模式卡在勿扰。
     */
    private fun reconcileState(
        settings: AppSettingsModel,
        courses: List<WidgetCourse>,
        now: LocalDateTime
    ) {
        val shouldBeOn = ReminderEngine.shouldModeBeOn(
            courses = courses,
            skippedDates = settings.skippedDates,
            date = now.date,
            time = now.time
        )
        val currentlyOn = AutoModeStateProbe.isModeOn(context, settings.autoControlMode)
        if (shouldBeOn == currentlyOn) return

        Log.i(TAG, "自动模式状态校准：当前=$currentlyOn 应为=$shouldBeOn")
        AutoModeController.toggle(context, shouldBeOn, settings.autoControlMode)
    }

    private companion object {
        const val TAG = "AutoModeScheduler"
    }
}
