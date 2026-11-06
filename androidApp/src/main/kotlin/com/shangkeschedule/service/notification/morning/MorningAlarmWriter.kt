package com.shangkeschedule.service.notification.morning

import android.content.Context
import android.util.Log
import com.shangkeschedule.notification.morning.MorningAlarmRegistryStore
import com.shangkeschedule.notification.morning.MorningAlarmSyncGate
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import com.shangkeschedule.service.notification.alarm.ForegroundGate
import kotlinx.datetime.LocalDateTime

/**
 * 早八闹钟登记簿的保留窗口（天）。
 *
 * 超出窗口的**过去**日期从登记簿丢弃，避免无界增长（每天一行、一年 365 行）；
 * 丢过去日期是安全的，因为早八计划只向前看，过去日期不会再被写入。
 *
 * 窗口必须**明显长于**计划前瞻窗口（7 天）：系统里那条删不掉的旧闹钟，
 * 一旦登记簿提前失忆，就会被当成「未登记」重复写一条。
 *
 * 放在文件级 `internal` 可见性，便于单测直接断言该契约。
 */
internal const val MORNING_ALARM_RETENTION_DAYS = 14

/**
 * 早八闹钟**写入编排**：把 [MorningAlarmPlan] 的结果落到系统时钟应用。
 *
 * 用户在系统闹钟页看到的就是普通闹钟，可自行修改/停用/删除——本应用尊重这类改动。
 *
 * ## 职责边界（2026-11-06 重构后）
 *
 * | 职责 | 归属 |
 * |---|---|
 * | 该不该写、写哪一条、要不要提示 | [MorningAlarmWriteDecision] |
 * | 登记簿怎么存、存哪里 | [MorningAlarmRegistryStore]（shared） |
 * | 登记簿回收窗口与条目淘汰 | [MorningAlarmRegistry.prune]（shared） |
 * | 探测时钟应用、发起系统 Activity | [SystemClockGateway] |
 * | 编排上面四者 + 前台门控 | **本类** |
 *
 * 重构前本类 339 行同时承担全部六件事（含 framework 调用与 prefs 名硬编码），
 * 既无法单测，也因「决策层已排除的情况」与「差分层再守一遍」产生死分支。
 *
 * ## 真机实测沿革（保留结论，勿回退）
 *
 * 1. **后台写入是静默失败的**（2026-09-27 真机）：写入靠
 *    `startActivity(ACTION_SET_ALARM)`，受 Android 10+ 后台启动限制（BAL）约束——
 *    后台发起时系统静默拦下，`startActivity` 不抛异常、闹钟也没建出来。旧实现按
 *    「有没有抛异常」判成功，于是把静默失败登记成「已写入」，登记簿被污染后不再补写
 *    （症状：开关为开、系统时钟里一条都没有，`shared_prefs/morning_alarm_registry.xml`
 *    在把 App 拉到前台前从未生成）。现在由 [ForegroundGate] 显式前置判断：
 *    非前台直接返回 [Result.Deferred] 且**不登记**。
 * 2. **`ACTION_SET_ALARM` 无法表达日期**：系统把它理解为「**下一次**该时刻」并建立
 *    **一次性**闹钟（本机 DeskClock 实测 `deleteAfterUse:true`）。实测请求
 *    09-28 13:15 / 09-29 09:45 / 09-30 07:05，三条**全部落在 09-28**，
 *    而标签写着 09-29 / 09-30。于是策略是「**只写最近一条 + 响过就滚动补位**」：
 *    登记簿记下真实响铃时刻，一旦该时刻已过就丢弃条目，下一条闹钟自然被补写。
 * 3. **系统闹钟删不掉、写入非幂等**：见 [MorningAlarmWriteDecision] 的类注释。
 */
class MorningAlarmWriter(
    private val context: Context,
    /**
     * 当前时刻（本地时区），用于三处判断：
     *  - 登记簿回收窗口（丢弃超过 [MORNING_ALARM_RETENTION_DAYS] 的过去条目）；
     *  - 筛选「最近一次尚未到来」的闹钟；
     *  - 判断哪条已登记的闹钟已经响过（一次性闹钟响过即被系统删除，需要补位）。
     * 由调用方注入而非内部 `System.now()`，便于单测与保持一致的时间基准。
     */
    private val now: LocalDateTime
) {

    private val clock = SystemClockGateway(context)

    /**
     * 把早八计划同步到系统时钟。
     *
     * 只维护**最近一次尚未到来**的那条闹钟：系统时钟 API 无法表达日期
     * （见 [MorningAlarmPlan.nextUpcoming]），写多条未来闹钟必然错日期；
     * 其余日期由「每日自愈 / 回到前台」滚动补位。
     *
     * @return 写入结果，供上层决定是否提示用户
     */
    fun sync(plan: List<MorningAlarmPlan.MorningAlarm>): Result = MorningAlarmSyncGate.run {
        syncLocked(plan)
    }

    private fun syncLocked(plan: List<MorningAlarmPlan.MorningAlarm>): Result {
        if (!clock.hasClockApp()) {
            val hasPendingCourse = MorningAlarmPlan.nextUpcoming(plan, now) != null
            val hasPendingOldAlarm = MorningAlarmRegistry
                .prune(loadRegistry(), now, MORNING_ALARM_RETENTION_DAYS)
                .isNotEmpty()
            if (!hasPendingCourse && !hasPendingOldAlarm) return Result.Unchanged
            Log.w(TAG, "无可用系统时钟应用，早八闹钟降级为应用内提醒")
            return Result.NoClockApp
        }

        val registry = loadRegistry()
        // 一次性迁移提示：v1 条目无法判断「是否真的写进去了 / 是否已响过」，
        // 会被 prune 全部丢弃并重写最近一条——请在日志里留下痕迹便于排查。
        val legacyCount = registry.values.count { it.firedAt == null }
        if (legacyCount > 0) {
            Log.i(
                TAG,
                "登记簿含 $legacyCount 条 v1 旧格式（只记了「intent 已发出」，无法判断是否写入/已响），" +
                    "本轮全部丢弃并重写最近一条；系统时钟里可能残留旧闹钟，需用户自行清理"
            )
        }
        // 登记簿回收必须**先**做：已响过的一次性闹钟需要在此失忆，下一条才会被补写。
        // 丢弃结果要落盘，所以下面即使没有任何写入动作也要保存。
        val persisted = MorningAlarmRegistry.prune(
            registry = registry,
            now = now,
            retentionDays = MORNING_ALARM_RETENTION_DAYS
        )

        val state = MorningAlarmWriteDecision.evaluate(
            plan = plan,
            registry = registry,
            now = now,
            visible = ForegroundGate.isAppVisible(context)
        )
        if (state != MorningAlarmWriteDecision.State.ReadyToWrite) {
            if (persisted != registry) saveRegistry(persisted)
            return when (state) {
                MorningAlarmWriteDecision.State.NoPlan,
                MorningAlarmWriteDecision.State.AlreadyRegistered -> Result.Unchanged
                MorningAlarmWriteDecision.State.BlockedByExisting -> Result.BlockedByExisting
                MorningAlarmWriteDecision.State.NotExpressible -> Result.NotExpressible
                MorningAlarmWriteDecision.State.Deferred -> Result.Deferred
                MorningAlarmWriteDecision.State.ReadyToWrite -> error("已处理可写状态")
            }
        }
        val next = requireNotNull(MorningAlarmPlan.nextUpcoming(plan, now))
        val targets = listOf(next)

        // 真机实测：本机（及多数 OEM）不支持按标签删除，故只做「未登记则写入」。
        // allowDismiss 保持明确传入，便于未来在确实支持的设备上开启差分删除。
        val decisions = MorningAlarmWriteDecision.actions(
            plan = targets,
            registry = persisted.mapValues { it.value.label },
            allowDismiss = clock.supportsLabelDismiss()
        )

        if (decisions.isEmpty()) {
            if (persisted != registry) saveRegistry(persisted)
            return Result.Unchanged
        }

        var wrote = 0
        val newRegistry = persisted.toMutableMap()

        for (decision in decisions) {
            when (decision) {
                is MorningAlarmWriteDecision.Decision.Dismiss -> {
                    clock.dismissByLabel(decision.label)
                    newRegistry.entries.removeAll { it.value.label == decision.label }
                }
                is MorningAlarmWriteDecision.Decision.Write -> {
                    if (clock.writeAlarm(decision.alarm, decision.label)) {
                        wrote++
                        // 记下**真实响铃时刻**：它是判断「这条已响过、可以写下一日」的唯一依据。
                        newRegistry[decision.alarm.courseDate.toString()] = MorningAlarmRegistry.Entry(
                            firedAt = LocalDateTime(decision.alarm.alarmDate, decision.alarm.alarmTime),
                            label = decision.label
                        )
                    }
                }
            }
        }

        saveRegistry(newRegistry)
        return if (wrote > 0) Result.Written(wrote) else Result.WriteFailed
    }

    /**
     * 停用早八闹钟（关闭开关时调用）。
     *
     * 真机实测：**无法删除系统闹钟**（DISMISS 不生效），所以这里不假装删除成功：
     * 只把本地登记状态标为停用，让上层如实提示用户到系统闹钟页自行清理。
     *
     * **注意：不要清空登记簿。** 登记簿是「已写入过哪些日期」的记忆；一旦清空，
     * 用户重新打开开关时这几天会被当成「未登记」再写一次，而旧闹钟又删不掉 ——
     * 直接产生重复闹钟。静默时我们**什么都不用做**（不写 = 不新增），
     * 但**保留记忆**（不丢 = 不重复）。
     */
    fun markDisabled() {
        val registry = loadRegistry()
        if (registry.isEmpty()) return
        Log.i(
            TAG,
            "早八闹钟已停用；登记簿保留 ${registry.size} 条历史（避免重新开启时重复写入）。" +
                "系统时钟中已写入的闹钟不会被自动清除，已引导用户自行处理"
        )
    }

    /**
     * 读取登记簿（`课程日期 -> 已写入条目`）。
     *
     * 编解码与持久化统一由 [MorningAlarmRegistryStore] 负责（含 v1 旧格式的兼容与淘汰）。
     */
    fun loadRegistry(): Map<String, MorningAlarmRegistry.Entry> =
        MorningAlarmRegistryStore.load(context)

    private fun saveRegistry(registry: Map<String, MorningAlarmRegistry.Entry>) {
        MorningAlarmRegistryStore.save(context, registry)
    }

    /** 打开系统闹钟列表页，方便用户直接管理。 */
    fun openSystemAlarmList(): Boolean = clock.openSystemAlarmList()

    /** 写入结果。 */
    sealed interface Result {
        /** 写入了 [count] 条闹钟。 */ data class Written(val count: Int) : Result
        /** 计划与已写入状态一致，无需改动（尊重用户改动）。 */ data object Unchanged : Result
        /** 未到期的旧系统闹钟不能可靠删除，新计划尚未写入。 */ data object BlockedByExisting : Result
        /** 系统时钟不支持指定日期，该计划暂不能准确写入。 */ data object NotExpressible : Result
        /** 启动系统时钟失败，本轮没有写入。 */ data object WriteFailed : Result
        /** 系统无可用时钟应用，需降级为应用内提醒。 */ data object NoClockApp : Result
        /**
         * 当前不在前台，写入会被后台启动限制静默拦下，故本次**不写也不登记**，
         * 留给下一次前台同步补上（见 [ForegroundGate] 与 [sync] 的前台守卫）。
         */
        data object Deferred : Result
    }

    private companion object {
        const val TAG = "MorningAlarmWriter"
    }
}
