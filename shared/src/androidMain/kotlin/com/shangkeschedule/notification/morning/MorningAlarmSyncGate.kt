package com.shangkeschedule.notification.morning

import android.content.Context

/** 多个调度入口与用户确认共用同一把锁。 */
object MorningAlarmSyncGate {
    private val lock = Any()
    fun <T> run(block: () -> T): T = synchronized(lock, block)

    /** 读取核对标记和写入必须持有同一把锁，核对期间不能创建新闹钟。 */
    fun <T> runIfReviewed(store: WeeklyAlarmConfirmationStore, block: () -> T): T? = run {
        if (runCatching { store.requiresReview() }.getOrDefault(true)) null else block()
    }

    /** 仅在用户确认已在系统时钟关闭旧上课闹钟后调用。 */
    fun clearVerifiedOldAlarms(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("morning_alarm_registry", Context.MODE_PRIVATE)
            .edit().remove("registry").commit()
}
