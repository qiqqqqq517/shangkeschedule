package com.shangkeschedule.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarIdentityMigrationTest {
    @Test
    fun `legacy calendar keeps its id and moves to the application account`() {
        val store = Store(CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE))
        assertEquals(41L, CalendarIdentityMigration.resolve(store))
        assertEquals(CalendarAccountIdentity.TYPE, store.calendars.single().accountType)
        assertEquals(listOf("account", "find", "migrate:41"), store.operations)
    }

    @Test
    fun `repeated sync reuses the migrated calendar without another migration`() {
        val store = Store(CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE))
        CalendarIdentityMigration.resolve(store)
        store.operations.clear()
        assertEquals(41L, CalendarIdentityMigration.resolve(store))
        assertEquals(listOf("account", "find"), store.operations)
    }

    @Test
    fun `account registration failure cannot move or create a calendar`() {
        val store = Store(CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE)).apply { accountAvailable = false }
        assertEquals(-1L, CalendarIdentityMigration.resolve(store))
        assertEquals(CalendarAccountIdentity.LEGACY_TYPE, store.calendars.single().accountType)
        assertEquals(listOf("account"), store.operations)
    }

    @Test
    fun `failed migration never creates a duplicate calendar`() {
        val store = Store(CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE)).apply { migrationSucceeds = false }
        assertEquals(-1L, CalendarIdentityMigration.resolve(store))
        assertEquals(listOf(CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE)), store.calendars)
        assertTrue("迁移失败不得另建日历", "create" !in store.operations)
    }

    @Test
    fun `fresh install registers the account before creating its calendar`() {
        val store = Store()
        assertEquals(100L, CalendarIdentityMigration.resolve(store))
        assertEquals(listOf("account", "find", "create"), store.operations)
        assertEquals(CalendarAccountIdentity.TYPE, store.calendars.single().accountType)
    }

    @Test
    fun `partially migrated calendars are preserved and the oldest id stays the sync target`() {
        val store = Store(
            CalendarIdentity(55, CalendarAccountIdentity.TYPE),
            CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE)
        )
        assertEquals(41L, CalendarIdentityMigration.resolve(store))
        assertEquals(listOf(55L, 41L), store.calendars.map { it.id })
        assertTrue(store.calendars.all { it.accountType == CalendarAccountIdentity.TYPE })
        assertTrue("已有日历不得重复创建", "create" !in store.operations)
    }

    @Test
    fun `unrelated account types are never migrated or selected`() {
        val store = Store(CalendarIdentity(9, "other.calendar"), CalendarIdentity(41, CalendarAccountIdentity.LEGACY_TYPE))
        assertEquals(41L, CalendarIdentityMigration.resolve(store))
        assertEquals("other.calendar", store.calendars.first().accountType)
        assertTrue("migrate:9" !in store.operations)
    }

    private class Store(vararg initial: CalendarIdentity) : CalendarIdentityStore {
        val calendars = initial.toMutableList()
        val operations = mutableListOf<String>()
        var accountAvailable = true
        var migrationSucceeds = true

        override fun ensureAccount(): Boolean {
            operations += "account"
            return accountAvailable
        }

        override fun findCalendars(): List<CalendarIdentity> {
            operations += "find"
            return calendars.toList()
        }

        override fun migrateLocalCalendar(id: Long): Boolean {
            operations += "migrate:$id"
            if (!migrationSucceeds) return false
            val index = calendars.indexOfFirst { it.id == id }
            calendars[index] = CalendarIdentity(id, CalendarAccountIdentity.TYPE)
            return true
        }

        override fun createCalendar(): Long {
            operations += "create"
            calendars += CalendarIdentity(100, CalendarAccountIdentity.TYPE)
            return 100
        }
    }
}
