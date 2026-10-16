package com.shangkeschedule.tool

/** 与 Android 账户元数据共用的账户类型；LOCAL 仅用于识别历史日历。 */
object CalendarAccountIdentity {
    const val TYPE = "com.shangkeschedule.calendar"
    const val LEGACY_TYPE = "LOCAL"
}

internal data class CalendarIdentity(val id: Long, val accountType: String)

/** 仅提供账户登记、读取和原位迁移，不提供删除日历或事件的入口。 */
internal interface CalendarIdentityStore {
    fun ensureAccount(): Boolean
    fun findCalendars(): List<CalendarIdentity>
    fun migrateLocalCalendar(id: Long): Boolean
    fun createCalendar(): Long
}

internal object CalendarIdentityMigration {
    fun resolve(store: CalendarIdentityStore): Long {
        // 必须先登记账户，否则 CalendarProvider 可能清理找不到对应账户的日历。
        if (!store.ensureAccount()) return -1L
        val calendars = store.findCalendars().filter {
            it.accountType == CalendarAccountIdentity.TYPE || it.accountType == CalendarAccountIdentity.LEGACY_TYPE
        }.sortedBy { it.id }
        for (calendar in calendars) {
            if (calendar.accountType == CalendarAccountIdentity.LEGACY_TYPE &&
                !store.migrateLocalCalendar(calendar.id)
            ) return -1L // 失败时保留现场，下次重试，不另外创建空日历。
        }
        return calendars.firstOrNull()?.id ?: store.createCalendar()
    }
}
