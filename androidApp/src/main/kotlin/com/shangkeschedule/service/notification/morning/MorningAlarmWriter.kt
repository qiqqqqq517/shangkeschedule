package com.shangkeschedule.service.notification.morning

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import androidx.core.content.edit
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import com.shangkeschedule.service.notification.alarm.ForegroundGate
import com.shangkeschedule.service.notification.notify.MorningAlarmNotifier
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
 * 早八闹钟写入器：把 [MorningAlarmPlan] 的结果落到**系统时钟应用**。
 *
 * 用户在系统闹钟页看到的就是普通闹钟，可自行修改/停用/删除——本应用尊重这类改动。
 *
 * ## API 约束（已查证本机 SDK `android-36/android/provider/AlarmClock.java`）
 *
 * 计划原稿假定可用 `AlarmClock.ACTION_DELETE_ALARM` + `EXTRA_ALARM_SEARCH_MODE_LABEL`
 * 做「每轮 delete-then-set」以保证不重复——**该常量在公版 SDK 中并不存在**
 * （`api-versions.xml` 亦无记录，`EXTRA_DELETE_AFTER_USE` 同样不存在）。
 * 真实可用的是 API 23 起的：
 *  - `ACTION_DISMISS_ALARM` + `EXTRA_ALARM_SEARCH_MODE=ALARM_SEARCH_MODE_LABEL`
 *  - `ACTION_SET_ALARM` + `EXTRA_SKIP_UI`
 *
 * 同时注意 `ACTION_DISMISS_ALARM` 是 **Activity Action**：多命中时系统会**弹出选择 UI**
 * 让用户挑。所以绝对不能「每轮无脑 delete-then-set」——那会反复弹窗。
 * 本实现改为**注册簿差分**：只有标签真正变化时才在前台发起一次删除 + 写入，
 * 幂等且不会打扰用户（[MorningAlarmDiff] 保证）。
 *
 * ## 真机实测（Xiaomi 22041216UC / MIUI V816 / Android 14）——**推翻了原设计的两条假设**
 *
 * 1. `ACTION_DISMISS_ALARM` 在本机**不能删除闹钟**：`am start` 发出后只是把时钟界面调到
 *    前台（当前焦点变成 `DeskClockTabActivity`），闹钟仍在 `dumpsys alarm` 与列表中。
 *    实测 LABEL 与 TIME 两种搜索模式均如此。DeskClock 的 manifest 把 DISMISS 与 SET
 *    一起挂在 `HandleSetAlarmActivity` 上，并无「搜索并删除」实现。
 * 2. `ACTION_SET_ALARM` **非幂等**：同一时刻 + 同一标签连发 2 次，列表出现 **2 条**重复闹钟。
 *    （曾误判为幂等——起因是首次查看时列表滚动位置不同，后续滚动核对才发现是 2 条。）
 *
 * 因此本实现改用「**只写一次，之后不再触碰**」策略（见 [MorningAlarmDiff]）：
 *  - 写入路径可靠（实测 SKIP_UI=true 会真的建立闹钟）；
 *  - 已登记过的日期一律不重写，避免重复堆积；
 *  - 不做删除动作（做不到），改由设置页提示用户到系统闹钟页手动管理。
 *
 * ## 真机实测（第二轮，2026-09-27）——**又推翻了两条假设**
 *
 * 症状：用户在设置页打开「早八闹钟」后，系统时钟里**一条都没有**。
 * 证据：`shared_prefs/morning_alarm_registry.xml` 在把 App 拉到前台之前**从未生成**
 * （= 从未成功写入过一条）；而前台一跑，DeskClock 日志立刻出现
 * `Added alarm rowId = 1/2/3`。
 *
 * 3. **后台写入是静默失败的。** 写入靠 `startActivity(ACTION_SET_ALARM)`，受 Android 10+
 *    后台启动限制（BAL）约束：后台（WorkManager）发起时系统**静默拦下**——
 *    `startActivity` 不抛异常、闹钟也没建出来。旧实现据此把「其实没写成功」登记成了成功，
 *    登记簿被永久污染，之后再也不补写。现在由 [ForegroundGate] 显式前置判断：
 *    非前台直接返回 [Result.Deferred] 且**不登记**。
 * 4. **`ACTION_SET_ALARM` 无法表达日期。** 系统把它理解为「**下一次**该时刻」并建立
 *    **一次性**闹钟（本机 DeskClock 实测 `deleteAfterUse:true`）。实测请求
 *    09-28 13:15 / 09-29 09:45 / 09-30 07:05，三条**全部落在 09-28**，
 *    而标签写着 09-29 / 09-30。
 *
 * 于是写入策略改为「**只写最近一条 + 响过就滚动补位**」：登记簿同时记下真实响铃时刻
 * （见 [MorningAlarmRegistry]），一旦该时刻已过就丢弃条目，下一条闹钟自然被补写。
 *
 * ## 平台差异
 *  - **写入**：`ACTION_SET_ALARM` + `EXTRA_SKIP_UI`，自 API 1 起可用，本机实测有效。
 *  - **删除**：[supportsLabelDismiss] 恒为 false（本机与多数 OEM 未实现）；
 *    保留该开关是为了未来在确实支持的设备上启用差分删除。
 *  - **无时钟应用**：`resolveActivity(ACTION_SET_ALARM)` 为空 ⇒ 标记降级，
 *    由 `NotificationScheduler` 改用应用内闹钟 + [MorningAlarmNotifier]（避免无提醒）。
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

    /**
     * 把早八计划同步到系统时钟。
     *
     * 只维护**最近一次尚未到来**的那条闹钟：系统时钟 API 无法表达日期
     * （见 [MorningAlarmPlan.nextUpcoming]），写多条未来闹钟必然错日期；
     * 其余日期由「每日自愈 / 回到前台」滚动补位。
     *
     * @return 写入结果，供上层决定是否提示用户
     */
    fun sync(plan: List<MorningAlarmPlan.MorningAlarm>): Result {
        if (!hasClockApp()) {
            Log.w(TAG, "无可用系统时钟应用，早八闹钟降级为应用内提醒")
            return Result.NoClockApp
        }

        // 前台守卫（本次修复的关键一处）：后台发起 startActivity 会被系统静默拦下，
        // 此时若照常登记，登记簿会把「其实没写成功」记成成功，之后永远不再补写。
        if (!ForegroundGate.isAppVisible(context)) {
            Log.w(TAG, "当前不在前台，跳过系统时钟写入（后台启动限制），待下次前台补写")
            return Result.Deferred
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

        val next = MorningAlarmPlan.nextUpcoming(plan, now)
        val targets = if (next != null && MorningAlarmPlan.expressibleBySystemClock(next, now)) {
            listOf(next)
        } else {
            if (next != null) {
                Log.w(TAG, "最近一条闹钟无法被系统时钟如实表达，本次跳过：$next")
            }
            emptyList()
        }

        // 真机实测：本机（及多数 OEM）不支持按标签删除，故只做「未登记则写入」。
        // allowDismiss 保持明确传入，便于未来在确实支持的设备上开启差分删除。
        val actions = MorningAlarmDiff.diff(
            plan = targets,
            registry = persisted.mapValues { it.value.label },
            allowDismiss = supportsLabelDismiss()
        )

        if (actions.isEmpty()) {
            if (persisted != registry) saveRegistry(persisted)
            return Result.Unchanged
        }

        var wrote = 0
        val newRegistry = persisted.toMutableMap()

        for (action in actions) {
            when (action) {
                is MorningAlarmDiff.Action.Dismiss -> {
                    dismissByLabel(action.label)
                    newRegistry.entries.removeAll { it.value.label == action.label }
                }
                is MorningAlarmDiff.Action.Write -> {
                    if (writeAlarm(action.alarm, action.label)) {
                        wrote++
                        // 记下**真实响铃时刻**：它是判断「这条已响过、可以写下一日」的唯一依据。
                        newRegistry[action.alarm.courseDate.toString()] = MorningAlarmRegistry.Entry(
                            firedAt = LocalDateTime(action.alarm.alarmDate, action.alarm.alarmTime),
                            label = action.label
                        )
                    }
                }
            }
        }

        saveRegistry(newRegistry)
        return Result.Written(wrote)
    }

    /**
     * 停用早八闹钟（关闭开关时调用）。
     *
     * 真机实测：**无法删除系统闹钟**（DISMISS 不生效），所以这里不假装删除成功：
     * 只把本地登记状态标为停用，让上层如实提示用户到系统闹钟页自行清理。
     *
     * **注意：不要清空登记簿。** 登记簿是「已写入过哪些日期」的记忆；一旦清空，
     * 用户重新打开开关时这 5 天会被当成「未登记」再写一次，而旧闹钟又删不掉 —— 
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
     * 编解码统一由 [MorningAlarmRegistry] 负责（含 v1 旧格式的兼容与淘汰）。
     */
    fun loadRegistry(): Map<String, MorningAlarmRegistry.Entry> = runCatching {
        prefs.getStringSet(KEY_REGISTRY, null)
            ?.mapNotNull { MorningAlarmRegistry.decode(it) }
            ?.toMap()
            ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun saveRegistry(registry: Map<String, MorningAlarmRegistry.Entry>) {
        // 旧格式条目（firedAt 为 null，不可信）不落盘：它们在 prune 阶段已被淘汰，
        // 这里再兜一层，保证写出去的永远是「记了真实响铃时刻」的 v2 编码。
        val encoded = registry.mapNotNull { (key, entry) ->
            val firedAt = entry.firedAt ?: return@mapNotNull null
            MorningAlarmRegistry.encode(key, firedAt, entry.label)
        }.toSet()
        prefs.edit { putStringSet(KEY_REGISTRY, encoded) }
    }

    // ---------------------------------------------------------------------
    // 系统时钟交互
    // ---------------------------------------------------------------------

    private fun hasClockApp(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        return runCatching {
            context.packageManager.resolveActivity(intent, 0) != null
        }.getOrDefault(false)
    }

    /**
     * 是否具备「按标签删除」能力。
     *
     * 公版 `AlarmClock.ACTION_DISMISS_ALARM` 的 LABEL 搜索语义**只是可选项**，
     * 且本机 MIUI 实测**不删除**（只把时钟调到前台）。公版 API 又**没有**
     * `ACTION_DELETE_ALARM`（经本机 SDK `android-36/android/provider/AlarmClock.java`
     * 与 `api-versions.xml` 网址核实：该常量不存在）。
     *
     * 因此这里**恒返回 false**：宁可不删，也不能重复写入造成堆积。
     */
    private fun supportsLabelDismiss(): Boolean = false

    /**
     * 按标签删除一条此前写入的闹钟。
     *
     * 用注册簿里的**完整精确标签**做搜索（不是前缀）：
     * 系统对 LABEL 的语义是「包含该词/短语」，标签越完整越不会命中用户自己的闹钟。
     */
    private fun dismissByLabel(label: String) {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startQuietly(intent, "按标签删除闹钟")
    }

    /** 写入一条系统闹钟（`EXTRA_SKIP_UI` = 不弹确认页）。 */
    private fun writeAlarm(alarm: MorningAlarmPlan.MorningAlarm, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.alarmTime.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.alarmTime.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            // SKIP_UI：不弹系统确认页直接写入（各 OEM 时钟应用支持度不一，失败会走 catch 降级）
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startQuietly(intent, "写入早八闹钟")
    }

    /**
     * 静默发起系统闹钟 Activity。
     *
     * 该路径受 Android 10+ 后台启动限制（BAL）约束：后台可能被系统拒绝
     * （此时会抛异常或静默失败）。故所有调用点都在**前台时机**
     * （设置页操作、MainActivity onResume），后台仅做标记，下次前台补写。
     */
    private fun startQuietly(intent: Intent, what: String): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "$what 失败：系统无对应 Activity", e)
        false
    } catch (e: Exception) {
        Log.w(TAG, "$what 失败（可能受后台启动限制）", e)
        false
    }

    /** 打开系统闹钟列表页，方便用户直接管理。 */
    fun openSystemAlarmList(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startQuietly(intent, "打开系统闹钟页")
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 写入结果。 */
    sealed interface Result {
        /** 写入了 [count] 条闹钟。 */ data class Written(val count: Int) : Result
        /** 计划与已写入状态一致，无需改动（尊重用户改动）。 */ data object Unchanged : Result
        /** 系统无可用时钟应用，需降级为应用内提醒。 */ data object NoClockApp : Result
        /**
         * 当前不在前台，写入会被后台启动限制静默拦下，故本次**不写也不登记**，
         * 留给下一次前台同步补上（见 [ForegroundGate] 与 [sync] 的前台守卫）。
         */
        data object Deferred : Result
    }

    private companion object {
        const val TAG = "MorningAlarmWriter"
        const val PREFS_NAME = "morning_alarm_registry"
        const val KEY_REGISTRY = "registry"
    }
}
