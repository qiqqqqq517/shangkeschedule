package com.shangkeschedule.notification.morning

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyAlarmConfirmationStoreTest {
    private val dir = Files.createTempDirectory("weekly-confirm-").toFile()
    private val store = WeeklyAlarmConfirmationStore(dir)
    private val schedule = mapOf(1 to "07:15", 3 to "08:00")

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    @Test fun onlyExplicitConfirmationCoversTheSameRevisionAndTimes() {
        assertTrue(store.confirmedFor("revision-a", schedule).isEmpty())
        assertTrue(store.confirm("revision-a", schedule))
        assertEquals(mapOf(DayOfWeek.MONDAY to LocalTime(7, 15), DayOfWeek.WEDNESDAY to LocalTime(8, 0)),
            WeeklyAlarmConfirmationStore(dir).confirmedFor("revision-a", schedule))
        assertTrue(store.confirmedFor("revision-b", schedule).isEmpty())
        assertTrue(store.confirmedFor("revision-a", mapOf(1 to "06:15")).isEmpty())
        assertTrue(store.confirmedFor("revision-a", emptyMap()).isEmpty())
    }

    @Test fun revertingConfigurationUnderNewRevisionCannotRestoreOldConfirmation() {
        assertTrue(store.confirm("revision-a", schedule))
        assertTrue(store.confirmedFor("revision-b", mapOf(1 to "06:30")).isEmpty())
        assertTrue(store.confirmedFor("revision-c", schedule).isEmpty())
    }

    @Test fun invalidInputsAndDamagedOrMissingStoreNeverConfirm() {
        assertFalse(store.confirm("", schedule))
        assertFalse(store.confirm("revision-a", mapOf(0 to "07:15")))
        assertFalse(store.confirm("revision-a", mapOf(1 to "27:15")))
        assertTrue(store.confirm("revision-a", schedule))
        dir.listFiles()!!.single().writeText("damaged", Charsets.UTF_8)
        assertTrue(store.confirmedFor("revision-a", schedule).isEmpty())
        val otherDevice = dir.resolve("other-device").apply { mkdir() }
        assertTrue(WeeklyAlarmConfirmationStore(otherDevice).confirmedFor("revision-a", schedule).isEmpty())
    }

    @Test fun clearingConfirmationDoesNotSuppressDailyAlarms() {
        assertTrue(store.confirm("revision-a", schedule))
        assertTrue(store.clear())
        assertTrue(store.confirmedFor("revision-a", schedule).isEmpty())
    }

    @Test fun interruptedReviewSurvivesReopenUntilCompleted() {
        assertFalse(store.requiresReview())
        assertTrue(store.beginReview())
        val afterRestart = WeeklyAlarmConfirmationStore(dir)
        assertTrue(afterRestart.requiresReview())
        assertTrue(afterRestart.completeReview { true })
        assertFalse(store.requiresReview())
    }

    @Test fun failedOrExceptionalSaveKeepsReviewPending() {
        assertTrue(store.beginReview())
        assertFalse(store.completeReview { false })
        assertTrue(store.requiresReview())
        assertFalse(store.completeReview { error("save failed") })
        assertTrue(store.requiresReview())
        assertTrue(store.completeReview { true })
        assertFalse(store.requiresReview())
    }

    @Test fun unavailableStoragePreventsReviewAndCannotBeTreatedAsSafe() {
        val invalidDirectory = dir.resolve("not-a-directory").apply { writeText("x") }
        val invalidStore = WeeklyAlarmConfirmationStore(invalidDirectory)
        assertFalse(invalidStore.beginReview())
        assertTrue(invalidStore.requiresReview())
        assertFalse(invalidStore.completeReview { error("must not save") })
    }

}
