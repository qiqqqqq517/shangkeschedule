package com.shangkeschedule.notification.plan

import kotlinx.datetime.LocalDateTime

/**
 * 上课自动勿扰/静音切换的「挂哪几次、槽位号是多少」**纯决策**。
 *
 * 切换序列本身由 [ReminderEngine.autoModeTransitions] 算出（含课间「关-开」同刻对合并、
 * 相邻同态去重），本对象只再回答两个问题：**丢掉已过期的**、**按槽位上限截断并编号**。
 *
 * 为什么单独存在：这段过滤+编号原先内联在 `AutoModeScheduler.schedule()` 中，
 * 而该类持有 `Context` / `Intent` / `AlarmManager`，在只有 JUnit4 的单测环境里无法实例化，
 * 于是「过期切换不能挂」「超过槽位上限要截断」这两条边界一条都测不到。
 * 槽位号必须与 `AlarmScheduler.setAutoMode(intent, index, …)` 的编号规则一致，
 * 否则撤销时会漏掉闹钟。
 */
object AutoModePlan {

    /**
     * 一次待挂的自动模式切换。
     *
     * @param enable    `true` = 上课开启（勿扰/静音），`false` = 下课恢复
     * @param triggerAt 绝对触发时刻
     * @param index     槽位序号（0 基），决定请求码 = 自动模式码基址 + [index]
     */
    data class Entry(
        val enable: Boolean,
        val triggerAt: LocalDateTime,
        val index: Int
    )

    /**
     * 选出本轮应当挂载的切换并编号。
     *
     * 规则：`triggerAt > now`（过期丢弃）→ 截断到 [slotLimit] 条 → 按顺序给 0..n-1 编号。
     *
     * @param transitions 已由 [ReminderEngine.autoModeTransitions] 归一化过的切换序列
     * @param now         当前时刻
     * @param slotLimit   可用的自动模式槽位数（通常为 `AlarmScheduler.autoModeSlotLimit`）；`<= 0` 时返回空
     */
    fun select(
        transitions: List<ReminderEngine.AutoModeTransition>,
        now: LocalDateTime,
        slotLimit: Int
    ): List<Entry> {
        if (slotLimit <= 0) return emptyList()

        return transitions
            .mapNotNull { transition ->
                val triggerAt = LocalDateTime(transition.date, transition.time)
                if (triggerAt <= now) null else triggerAt to transition.enable
            }
            .take(slotLimit)
            .mapIndexed { index, (triggerAt, enable) ->
                Entry(enable = enable, triggerAt = triggerAt, index = index)
            }
    }
}
