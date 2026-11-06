package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import kotlinx.datetime.LocalDateTime

/**
 * 早八闹钟**写入决策**：给定「计划 + 已登记状态 + 当前时刻 + 是否前台」，
 * 回答本轮到底该做什么。
 *
 * ## 为什么把「该不该写」和「差分动作」合到一个对象
 *
 * 拆成 `MorningAlarmWriteDecision`（判状态）+ 一个独立的差分对象（算动作）两层后，
 * 两者的前置条件重复且**不一致**：
 *  - `WriteDecision` 在 `unexpired.isNotEmpty()` 时返回 `BlockedByExisting`；
 *  - `Diff` 里另有一道 `if (!allowDismiss && registry.isNotEmpty()) return emptyList()`。
 *
 * 由于 `ReadyToWrite` 只在登记簿为空时出现，旧差分对象里那道守卫**永远不可能触发**——
 * 它守的是 `WriteDecision` 已经排除掉的情况。合成本对象后只剩一处判断，语义单一。
 *
 * ## 真机实测约束（Xiaomi 22041216UC / MIUI V816 / Android 14）
 *
 * 1. `ACTION_DISMISS_ALARM` + `EXTRA_ALARM_SEARCH_MODE=LABEL|TIME` **不会删除**闹钟——
 *    MIUI 时钟把 DISMISS 也路由到 `HandleSetAlarmActivity`，只把时钟界面调到前台。
 *    公版 DeskClock 未实现「搜索并删除」语义。
 * 2. `ACTION_SET_ALARM` **不是幂等的**：同一时刻、同一标签连发两次会留下两条重复闹钟。
 * 3. 结论：**在无法可靠删除的前提下，绝不能重复写入**，否则会在用户的系统闹钟页
 *    不断堆积重复项。
 *
 * 因此策略是「**只写一次，之后不再触碰**」：已登记日期一律不重写（即使标签变了），
 * 计划里消失的日期也不做删除动作（删不掉，且删除会打断用户自己的修改）。
 *
 * 未到期登记项尚存且无法删除时，不能另写不同日期的闹钟。
 */
internal object MorningAlarmWriteDecision {

    /** 决策结果。 */
    sealed interface Decision {
        /** 写入这一条系统闹钟。 */
        data class Write(val alarm: MorningAlarmPlan.MorningAlarm, val label: String) : Decision

        /**
         * 按标签删除一条此前写入的闹钟。
         *
         * 仅在系统确实实现了搜索删除时才会产出；真机实测 MIUI 不支持，故正常情况下不会出现。
         */
        data class Dismiss(val label: String) : Decision
    }

    /** 本轮状态。 */
    enum class State {
        /** 计划里没有任何未来闹钟，且登记簿也干净 —— 无事可做。 */
        NoPlan,

        /** 最近一条已登记且与计划完全一致 —— 尊重现状，不重写。 */
        AlreadyRegistered,

        /** 有未到期的旧闹钟、而系统删不掉它 —— 不能再写第二条。 */
        BlockedByExisting,

        /** 系统时钟只能表达「下一次该时刻」，这条计划的日期不是那一次 —— 宁可不写。 */
        NotExpressible,

        /** 应用不在前台，写入会被 BAL 静默拦下 —— 本轮不写也不登记。 */
        Deferred,

        /** 可以写。 */
        ReadyToWrite
    }

    /**
     * 计算本轮状态。
     *
     * @param plan 新算出的早八计划（已按用户确认的每周覆盖过滤过）
     * @param registry 已写入登记簿
     * @param now 当前时刻
     * @param visible 应用是否前台可见（决定能否发起系统 Activity）
     */
    fun evaluate(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        registry: Map<String, MorningAlarmRegistry.Entry>,
        now: LocalDateTime,
        visible: Boolean
    ): State {
        val unexpired = MorningAlarmRegistry.prune(registry, now, MORNING_ALARM_RETENTION_DAYS)
        val next = MorningAlarmPlan.nextUpcoming(plan, now)
            ?: return if (unexpired.isEmpty()) State.NoPlan else State.BlockedByExisting
        val targetTime = LocalDateTime(next.alarmDate, next.alarmTime)
        val registered = unexpired[next.courseDate.toString()]
        if (unexpired.size == 1 && registered?.firedAt == targetTime && registered.label == MorningAlarmPlan.label(next)) {
            return State.AlreadyRegistered
        }
        if (unexpired.isNotEmpty()) return State.BlockedByExisting
        if (!MorningAlarmPlan.expressibleBySystemClock(next, now)) return State.NotExpressible
        if (!visible) return State.Deferred
        return State.ReadyToWrite
    }

    /**
     * 为 [State.ReadyToWrite] 算出具体动作。
     *
     * 前置条件由 [evaluate] 保证：登记簿已清空到「无未到期条目」，且目标闹钟可表达。
     * 所以这里不需要再判「登记簿非空就停手」——那道旧守卫在此状态下恒为假。
     *
     * @param plan 新算出的早八计划
     * @param registry 已写入登记簿（调用方应先按回收窗口裁剪）
     * @param allowDismiss 系统是否支持按标签删除（实测多数 OEM 不支持，默认 false）
     */
    fun actions(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        registry: Map<String, String>,
        allowDismiss: Boolean = false
    ): List<Decision> {
        val decisions = mutableListOf<Decision>()

        for (alarm in plan) {
            val key = alarm.courseDate.toString()
            val label = MorningAlarmPlan.label(alarm)
            val existing = registry[key]

            when {
                // 从未登记 → 写入（这是唯一会写系统时钟的路径）
                existing == null -> decisions += Decision.Write(alarm, label)

                // 已登记且标签一致 → 无需动作
                existing == label -> Unit

                // 已登记但标签变了：
                //  - 系统支持删除：先删旧再写新，保证内容最新
                //  - 系统不支持删除（实测 MIUI）：**不动**。重写会造成重复堆积（实测非幂等），
                //    而删除做不到；此时尊重系统里已有的那一条，由用户自行调整。
                allowDismiss -> {
                    decisions += Decision.Dismiss(existing)
                    decisions += Decision.Write(alarm, label)
                }
                else -> Unit
            }
        }

        // 计划里已消失的日期：仅当系统支持删除时才产出删除动作。
        // 不支持删除时保持沉默（删不掉；且不该让调用方以为清理成功了）。
        if (allowDismiss) {
            val plannedKeys = plan.map { it.courseDate.toString() }.toSet()
            for ((key, label) in registry) {
                if (key !in plannedKeys) decisions += Decision.Dismiss(label)
            }
        }

        return decisions
    }
}
