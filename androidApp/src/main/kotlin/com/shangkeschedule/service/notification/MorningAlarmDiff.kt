package com.shangkeschedule.service.notification

import com.shangkeschedule.notification.MorningAlarmPlan

/**
 * 早八闹钟「计划 ↔ 已写入注册簿」的纯逻辑差分。
 *
 * 单独抽出来是因为**这是唯一需要精确推理、且不该触碰 Android framework 的部分**：
 * 系统时钟的写入/删除都必须通过 Activity Action（见 [MorningAlarmWriter]），
 * 无法在单测里真实执行，所以把「该做什么」与「怎么做到」分离——
 * 本对象只算动作，可在 `androidApp/src/test` 用纯 JUnit 覆盖。
 *
 * ## 真机实测约束（Xiaomi 22041216UC / MIUI V816 / Android 14）
 *
 * 实测结论（探针闹钟，已清理）：
 *  1. `ACTION_DISMISS_ALARM` + `EXTRA_ALARM_SEARCH_MODE=LABEL|TIME` **不会删除**闹钟——
 *     MIUI 时钟把 DISMISS 也路由到 `HandleSetAlarmActivity`，只把时钟界面调到前台。
 *     公版 DeskClock 未实现「搜索并删除」语义。
 *  2. `ACTION_SET_ALARM` **不是幂等的**：同一时刻、同一标签连发两次会留下两条重复闹钟
 *     （实测连发 2 次「07:25 / 同名标签」→ 列表出现 2 条）。
 *  3. 结论：**在无法可靠删除的前提下，绝不能重复写入**，否则会在用户的系统闹钟页
 *     不断堆积重复项。
 *
 * 因此策略调整为「**只写一次，之后不再触碰**」：
 *  - 注册簿里已登记的日期**一律不重写**（即使标签变了），把最终调整权交给用户；
 *  - 只有**从未登记过**的日期才写入；
 *  - 计划里消失的日期**不做删除动作**（删不掉，且删除会打断用户自己的修改），
 *    仅在注册簿里标记为「已失效」，由设置页提示用户到系统闹钟页手动清理。
 *
 * 这样即便删除能力缺失，也保证：**任何一条闹钟一个日期只写入一次，永不堆积**。
 */
object MorningAlarmDiff {

    /** 差分产出的一个动作。 */
    sealed interface Action {
        /** 写入一条系统闹钟。 */
        data class Write(val alarm: MorningAlarmPlan.MorningAlarm, val label: String) : Action

        /**
         * 按标签删除一条此前写入的闹钟。
         *
         * 仅在 [MorningAlarmWriter.supportsLabelDismiss]（系统确实实现了搜索删除）时才会产出；
         * 真机实测 MIUI 不支持，故正常情况下不会生成此动作。
         */
        data class Dismiss(val label: String) : Action
    }

    /**
     * 计算需要执行的写入动作。
     *
     * @param plan 新算出的早八计划
     * @param registry 已写入注册簿：`课程日期字符串 -> 已写入的标签`
     * @param allowDismiss 系统是否支持按标签删除（实测多数 OEM 不支持，默认 false）
     */
    fun diff(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        registry: Map<String, String>,
        allowDismiss: Boolean = false
    ): List<Action> {
        val actions = mutableListOf<Action>()

        for (alarm in plan) {
            val key = alarm.courseDate.toString()
            val label = MorningAlarmPlan.label(alarm)
            val existing = registry[key]

            when {
                // 从未登记 → 写入（这是唯一会写系统时钟的路径）
                existing == null -> actions += Action.Write(alarm, label)

                // 已登记且标签一致 → 无需动作
                existing == label -> Unit

                // 已登记但标签变了：
                //  - 系统支持删除：先删旧再写新，保证内容最新
                //  - 系统不支持删除（实测 MIUI）：**不动**。重写会造成重复堆积（实测非幂等），
                //    而删除做不到；此时尊重系统里已有的那一条，由用户自行调整。
                allowDismiss -> {
                    actions += Action.Dismiss(existing)
                    actions += Action.Write(alarm, label)
                }
                else -> Unit
            }
        }

        // 计划里已消失的日期：仅当系统支持删除时才产出删除动作。
        // 不支持删除时保持沉默（删不掉；且不该让调用方以为清理成功了）。
        if (allowDismiss) {
            val plannedKeys = plan.map { it.courseDate.toString() }.toSet()
            for ((key, label) in registry) {
                if (key !in plannedKeys) actions += Action.Dismiss(label)
            }
        }

        return actions
    }
}
