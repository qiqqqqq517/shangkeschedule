package com.shangkeschedule.notification.morning

import java.nio.file.Files
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorningAlarmWriteStatusStoreTest {
    @Test fun statusSurvivesReopenAndClear() {
        val dir = Files.createTempDirectory("morning-write-status").toFile()
        val status = MorningAlarmWriteStatusStore.Status(
            MorningAlarmWriteStatusStore.Kind.DEFERRED, LocalDateTime.parse("2026-09-29T07:15:00")
        )
        assertTrue(MorningAlarmWriteStatusStore(dir).save(status))
        val reopened = MorningAlarmWriteStatusStore(dir)
        assertEquals(status, reopened.read())
        assertTrue(reopened.clear())
        assertEquals(null, MorningAlarmWriteStatusStore(dir).read())
    }

    @Test fun corruptedOrUnavailableStateDoesNotLookClear() {
        val dir = Files.createTempDirectory("morning-write-corrupt").toFile()
        dir.resolve("morning_alarm_write_status.txt").writeText("unexpected")
        assertEquals(MorningAlarmWriteStatusStore.Kind.UNKNOWN, MorningAlarmWriteStatusStore(dir).read()?.kind)
        val unavailable = Files.createTempFile("morning-write-unavailable", ".tmp").toFile()
        assertEquals(MorningAlarmWriteStatusStore.Kind.UNKNOWN, MorningAlarmWriteStatusStore(unavailable).read()?.kind)
    }
}
