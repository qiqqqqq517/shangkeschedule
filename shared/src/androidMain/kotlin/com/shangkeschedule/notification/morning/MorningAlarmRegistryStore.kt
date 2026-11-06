package com.shangkeschedule.notification.morning

import android.content.Context
import com.shangkeschedule.notification.registry.MorningAlarmRegistry

/**
 * 早八闹钟登记簿的**持久化**（`SharedPreferences`），只做读写、不做决策。
 *
 * ## 为什么单独成类
 *
 * 登记簿的 prefs 名此前在**两个模块各硬编码一次**：
 *  - `MorningAlarmWriter.PREFS_NAME`（androidApp，写入与读取）
 *  - `MorningAlarmSyncGate.clearVerifiedOldAlarms`（shared，用户确认后清空）
 *
 * 两处字面量必须逐字一致，改一处就变成「清空了一个不存在的文件、真正的登记簿毫发无损」
 * —— 而症状是「用户已在系统时钟删掉旧闹钟，应用却仍认为有未到期闹钟、新闹钟永不写入」，
 * 排查时完全看不出是名字写错。故把名字与读写收敛到本类，任何调用方都不再出现字面量。
 *
 * 决策（该不该写、写哪一条、什么时候丢弃条目）仍留在 `MorningAlarmWriter` 与
 * [MorningAlarmRegistry.prune]，本类保持「哑存储」。
 */
object MorningAlarmRegistryStore {

    /** 登记簿所在的 `SharedPreferences` 文件名。**唯一**定义处，不要在别处再写字面量。 */
    const val PREFS_NAME = "morning_alarm_registry"

    private const val KEY_REGISTRY = "registry"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读取登记簿（`课程日期 -> 已写入条目`）。
     *
     * 编解码统一由 [MorningAlarmRegistry] 负责（含 v1 旧格式的兼容与淘汰）。
     * 读失败返回空表而不是抛异常：空表只会导致本轮「看起来没写过」，
     * 而抛出会让整条早八排程中断。
     */
    fun load(context: Context): Map<String, MorningAlarmRegistry.Entry> = runCatching {
        prefs(context).getStringSet(KEY_REGISTRY, null)
            ?.mapNotNull { MorningAlarmRegistry.decode(it) }
            ?.toMap()
            ?: emptyMap()
    }.getOrDefault(emptyMap())

    /**
     * 保存登记簿。
     *
     * 旧格式条目（`firedAt` 为 null，不可信）不落盘：它们在 `prune` 阶段已被淘汰，
     * 这里再兜一层，保证写出去的永远是「记了真实响铃时刻」的 v2 编码。
     */
    fun save(context: Context, registry: Map<String, MorningAlarmRegistry.Entry>) {
        val encoded = registry.mapNotNull { (key, entry) ->
            val firedAt = entry.firedAt ?: return@mapNotNull null
            MorningAlarmRegistry.encode(key, firedAt, entry.label)
        }.toSet()
        prefs(context).edit().putStringSet(KEY_REGISTRY, encoded).apply()
    }

    /**
     * 清空登记簿。
     *
     * **只应在用户已确认在系统时钟里关闭旧闹钟后调用**（见
     * `MorningAlarmSyncGate.clearVerifiedOldAlarms`）。普通停用开关**不得**走这里：
     * 系统闹钟普遍删不掉，登记簿一旦失忆，重新开启时同几天会被当成「未登记」再写一次，
     * 直接产生重复闹钟。
     */
    fun clear(context: Context): Boolean =
        prefs(context).edit().remove(KEY_REGISTRY).commit()
}
