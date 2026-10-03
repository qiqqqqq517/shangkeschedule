package com.shangkeschedule.service.notification.notify

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService

/**
 * 已投递通知登记簿（occKey → 通知 ID）。
 *
 * 背景（旧实现病根）：`CourseNotificationWorker.cancelAllAlarms()` 只做
 * `AlarmManager.cancel()`，**从不调用 `NotificationManager.cancel()`**。
 * 而课程提醒通知在非兼容模式下是 `setOngoing(true)` 的常驻通知——
 * 于是每次重排后，旧提醒会一直挂在状态栏，直到用户手动逐条关闭：
 * 课表变更越频繁，状态栏堆积越多「上课提醒」。
 *
 * 修复：所有主动投递的课程提醒通知先行登记，在**两个**时机统一回收——
 *  1. 重新排程（课表/设置变更）：`pruneExcept` 回收「已不在新计划里」的那些
 *  2. 关闭课程提醒总开关：`clear` 全部回收
 *
 * 升级迁移的清理由 `LegacyAlarmMigrator` 负责（按旧槽位 ID 区间直接 `cancel()`），
 * **不经本登记簿**：旧版通知从未登记过，登记簿里没有它们的记录。
 *
 * 用 SharedPreferences 而非内存集合：投递发生在广播接收器（可能由系统冷启动进程），
 * 回收发生在 Worker，两者不共享内存。
 */
class PostedNotificationRegistry(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 登记一条已投递的通知。 */
    fun register(occurrenceKey: String, notificationId: Int) {
        runCatching {
            persist(load() + (occurrenceKey to notificationId))
        }.onFailure { Log.w(TAG, "登记通知失败", it) }
    }

    /** 当前登记的全部「occKey → 通知 ID」。 */
    fun load(): Map<String, Int> = runCatching {
        prefs.getStringSet(KEY_ENTRIES, null)
            ?.mapNotNull { entry ->
                // 前缀式存储，split 限 1 次 → occKey 内部含任何字符都不会切错
                val parts = entry.split(ENTRY_SEPARATOR, limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                parts[1] to id
            }
            ?.toMap()
            ?: emptyMap()
    }.getOrDefault(emptyMap())

    /**
     * 回收**不在** [keepOccurrenceKeys] 中的通知，并从登记簿移除。
     *
     * 重排时调用：新计划里仍存在的提醒保留（不闪断），
     * 已删除/改期的课程提醒则连带通知一起清掉。
     *
     * @return 实际取消的通知条数
     */
    fun pruneExcept(keepOccurrenceKeys: Set<String>): Int {
        val current = load()
        val stale = current.filterKeys { it !in keepOccurrenceKeys }
        if (stale.isEmpty()) return 0
        val nm = appContext.getSystemService<NotificationManager>()
        stale.values.forEach { id ->
            runCatching { nm?.cancel(id) }.onFailure { Log.w(TAG, "取消陈旧通知失败 id=$id", it) }
        }
        persist(current.filterKeys { it in keepOccurrenceKeys })
        Log.d(TAG, "已回收 ${stale.size} 条陈旧课程提醒通知")
        return stale.size
    }

    /** 回收全部已登记通知（关闭课程提醒总开关）。 */
    fun clear(): Int {
        val current = load()
        if (current.isEmpty()) return 0
        val nm = appContext.getSystemService<NotificationManager>()
        current.values.forEach { id ->
            runCatching { nm?.cancel(id) }.onFailure { Log.w(TAG, "取消通知失败 id=$id", it) }
        }
        persist(emptyMap())
        Log.d(TAG, "已回收全部 ${current.size} 条已登记通知")
        return current.size
    }

    /**
     * 用户点了某条提醒的「关闭」：从登记簿移除该通知 ID。
     * 通知本身已由调用方的 `NotificationManager.cancel` 关掉，这里只做登记清理。
     */
    fun remove(notificationId: Int) {
        runCatching {
            val current = load()
            if (current.none { it.value == notificationId }) return@runCatching
            persist(current.filterValues { it != notificationId })
        }.onFailure { Log.w(TAG, "移除通知登记失败 id=$notificationId", it) }
    }

    private fun persist(entries: Map<String, Int>) {
        runCatching {
            prefs.edit {
                putStringSet(
                    KEY_ENTRIES,
                    entries.map { "${it.value}$ENTRY_SEPARATOR${it.key}" }.toSet()
                )
            }
        }.onFailure { Log.w(TAG, "持久化通知登记簿失败", it) }
    }

    private companion object {
        const val TAG = "PostedNotifRegistry"
        const val PREFS_NAME = "posted_notification_registry"
        const val KEY_ENTRIES = "entries"

        /**
         * 条目分隔符。
         *
         * 必须是 occKey 内部**绝不出现**的字符：`NotificationIds.occurrenceKey` 用
         * `\u0001` 拼接三段，故这里改用 `\u0002`；同时又配合 `split(limit = 2)`
         * （ID 在前、键在后），双重保证课程 ID 含任意字符也不会切错。
         */
        const val ENTRY_SEPARATOR = "\u0002"
    }
}
