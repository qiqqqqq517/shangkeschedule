package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Test

class MorningAlarmRiskSignalsTest {
    @Test fun watchesFullCoursesConfigAndEverySchemeOnlyWhileHybridEnabled() = runBlocking {
        val settings = MutableStateFlow(AppSettingsModel(currentCourseTableId = "table", morningAlarmEnabled = true,
            morningAlarmMode = MorningAlarmMode.HYBRID_WEEKLY))
        val courses = MutableStateFlow(listOf("week1", "week20"))
        val config = MutableStateFlow("config")
        val schemes = MutableStateFlow(listOf("default", "winter"))
        val ids = MutableStateFlow(listOf("default", "winter"))
        val slots = mapOf("default" to MutableStateFlow("08:00"), "winter" to MutableStateFlow("09:00"),
            "summer" to MutableStateFlow("07:30"))
        val observed = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            morningAlarmRiskSignals(settings, { courses }, { config }, { schemes }, { ids }, { _, id -> slots.getValue(id) })
                .collect { observed.send(Unit) }
        }
        suspend fun next() = withTimeout(3000) { observed.receive() }
        next()
        courses.value = listOf("week1", "week20 changed")
        next()
        config.value = "new semester start"
        next()
        schemes.value = listOf("default", "winter new date")
        next()
        slots.getValue("winter").value = "09:30"
        next()
        ids.value = listOf("default", "winter", "summer")
        next()
        slots.getValue("summer").value = "07:45"
        next()
        settings.value = settings.value.copy(skippedDates = setOf("2026-12-14"))
        next()
        settings.value = settings.value.copy(currentCourseTableId = "")
        next()
        settings.value = settings.value.copy(currentCourseTableId = "table")
        next()
        settings.value = settings.value.copy(morningAlarmMode = MorningAlarmMode.SMART_DAILY)
        delay(100)
        courses.value = listOf("other change")
        assertNull(withTimeoutOrNull(250) { observed.receive() })
        job.cancel()
    }
}
