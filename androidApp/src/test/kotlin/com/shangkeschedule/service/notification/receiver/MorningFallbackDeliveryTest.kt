package com.shangkeschedule.service.notification.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class MorningFallbackDeliveryTest {
    @Test fun staleAlarmDoesNotPostAfterSwitchIsDisabled() {
        var enabled = true
        var posts = 0
        enabled = false
        dispatchMorningFallback({ enabled }) { posts++ }
        assertEquals(0, posts)
        enabled = true
        dispatchMorningFallback({ enabled }) { posts++ }
        assertEquals(1, posts)
    }

    @Test fun unreadableSettingCannotPost() {
        var posts = 0
        try {
            dispatchMorningFallback({ error("read failed") }) { posts++ }
        } catch (_: IllegalStateException) { }
        assertEquals(0, posts)
    }
}
