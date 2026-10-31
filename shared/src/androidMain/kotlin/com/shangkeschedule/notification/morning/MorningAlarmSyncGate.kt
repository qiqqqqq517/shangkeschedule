package com.shangkeschedule.notification.morning

import android.content.Context

/** 多个调度入口与用户确认共用同一把锁。 */
object MorningAlarmSyncGate {
    private val lock = Any()
    fun <T> run(block: () -> T): T = synchronized(lock, block)

    /** 仅在用户确认已在系统时钟关闭旧上课闹钟后调用。 */
    fun clearVerifiedOldAlarms(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("morning_alarm_registry", Context.MODE_PRIVATE)
            .edit().remove("registry").commit()
}
