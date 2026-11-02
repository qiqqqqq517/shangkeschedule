package com.shangkeschedule.notification.morning

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MorningAlarmSyncGateTest {
    @Test fun reviewMarkerBlocksWritesAfterRestartUntilUserCompletesReview() {
        val dir = Files.createTempDirectory("morning-review-gate").toFile()
        val first = WeeklyAlarmConfirmationStore(dir)
        var writes = 0
        assertEquals(1, MorningAlarmSyncGate.runIfReviewed(first) { ++writes })
        assertEquals(true, MorningAlarmSyncGate.run { first.beginReview() })

        val afterRestart = WeeklyAlarmConfirmationStore(dir)
        assertNull(MorningAlarmSyncGate.runIfReviewed(afterRestart) { ++writes })
        assertEquals(1, writes)

        assertEquals(true, MorningAlarmSyncGate.run { afterRestart.completeReview { true } })
        assertEquals(2, MorningAlarmSyncGate.runIfReviewed(afterRestart) { ++writes })
    }

    @Test fun unreadableReviewStateBlocksWrites() {
        val dir = Files.createTempFile("morning-review-gate", ".tmp").toFile()
        val store = WeeklyAlarmConfirmationStore(dir)
        var writes = 0
        assertNull(MorningAlarmSyncGate.runIfReviewed(store) { ++writes })
        assertEquals(0, writes)
    }
}
