package com.shangkeschedule.data.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.shangkeschedule.data.db.main.CourseTableConfigDao
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class MorningAlarmSettingsTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        var writes = 0
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            return transform(state.value).also { state.value = it }
        }
    }

    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, method, _ -> if (method.name == "getFirstTableFlow") flowOf(null) else null } as T

    @Test
    fun weeklyPreferenceSurvivesBackupRoundTrip() {
        val json = CourseImportExport.json
        val original = """{"morningAlarmMode":"HYBRID_WEEKLY","morningAlarmWeeklyTimes":{"1":"07:15","3":"08:00"}}"""
        val decoded = json.decodeFromString(CourseImportExport.AppSettingsBackupModel.serializer(), original)
        val encoded = json.encodeToString(CourseImportExport.AppSettingsBackupModel.serializer(), decoded)
        val output = json.parseToJsonElement(encoded).jsonObject
        assertEquals("HYBRID_WEEKLY", output["morningAlarmMode"]?.jsonPrimitive?.content)
        val times = output["morningAlarmWeeklyTimes"]?.jsonObject
        assertEquals("07:15", times?.get("1")?.jsonPrimitive?.content)
        assertEquals("08:00", times?.get("3")?.jsonPrimitive?.content)
        val old = json.decodeFromString(CourseImportExport.AppSettingsBackupModel.serializer(), "{}")
        assertNull(old.morningAlarmMode)
        assertNull(old.morningAlarmWeeklyTimes)
    }

    @Test
    fun oldAndInvalidPreferencesDoNotEnableWeeklyMode() {
        val old = AppSettingsModel.fromPreferences(emptyPreferences(), "")
        assertEquals(MorningAlarmMode.SMART_DAILY, old.morningAlarmMode)
        assertTrue(old.morningAlarmWeeklyTimes.isEmpty())
        assertEquals("", old.morningAlarmWeeklyRevision)
        val bad = mutablePreferencesOf(
            AppSettingsModel.KEY_MORNING_ALARM_MODE to "future-mode",
            AppSettingsModel.KEY_MORNING_ALARM_WEEKLY_TIMES to setOf("1=07:15", "2=25:00", "3=7:00", "8=08:00", "4=09:00", "4=10:00")
        )
        val restored = AppSettingsModel.fromPreferences(bad, "")
        assertEquals(MorningAlarmMode.SMART_DAILY, restored.morningAlarmMode)
        assertEquals(mapOf(1 to "07:15"), restored.morningAlarmWeeklyTimes)
    }

    @Test
    fun savingAndRestoringSamePlanInvalidatesPreviousConfirmationAtomically() = runBlocking {
        val store = MemoryStore()
        val settings = AppSettingsRepository(store, MemoryStore(), dao<CourseTableDao>(), dao<CourseTableConfigDao>())
        val plan = mapOf(1 to "07:15", 2 to "8:00", 8 to "08:00")
        settings.updateMorningAlarmSchedule(MorningAlarmMode.HYBRID_WEEKLY, plan)
        val first = settings.getAppSettingsOnce()
        assertEquals(1, store.writes)
        assertEquals(MorningAlarmMode.HYBRID_WEEKLY, first.morningAlarmMode)
        assertEquals(mapOf(1 to "07:15"), first.morningAlarmWeeklyTimes)
        assertTrue(first.morningAlarmWeeklyRevision.isNotEmpty())

        settings.updateMorningAlarmSchedule(MorningAlarmMode.SMART_DAILY, emptyMap())
        settings.updateMorningAlarmSchedule(MorningAlarmMode.HYBRID_WEEKLY, plan)
        val again = settings.getAppSettingsOnce()
        assertNotEquals(first.morningAlarmWeeklyRevision, again.morningAlarmWeeklyRevision)

        settings.updateSupplementalSettingsAtomically(again)
        val afterRestore = settings.getAppSettingsOnce()
        assertEquals(again.morningAlarmWeeklyTimes, afterRestore.morningAlarmWeeklyTimes)
        assertNotEquals(again.morningAlarmWeeklyRevision, afterRestore.morningAlarmWeeklyRevision)

        settings.updateMorningAlarmEnabled(true)
        val afterToggle = settings.getAppSettingsOnce()
        assertNotEquals(afterRestore.morningAlarmWeeklyRevision, afterToggle.morningAlarmWeeklyRevision)
        settings.updateMorningAlarmLeadMinutes(60)
        assertNotEquals(afterToggle.morningAlarmWeeklyRevision, settings.getAppSettingsOnce().morningAlarmWeeklyRevision)
    }
}
