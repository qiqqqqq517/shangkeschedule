package com.shangkeschedule.service.notification.control

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.shangkeschedule.data.model.AutoControlMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class AutoModeOwnershipTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val notification get() = context.getSystemService(NotificationManager::class.java)
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Before fun setup() {
        context.getSharedPreferences("auto_mode_controller", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(notification).setNotificationPolicyAccessGranted(true)
        notification.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @Test fun disablingWithoutOwnershipPreservesManualDnd() {
        notification.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertTrue(AutoModeController.toggle(context, false, AutoControlMode.DND))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notification.currentInterruptionFilter)
    }

    @Test fun disablingWithoutOwnershipPreservesManualSilent() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertTrue(AutoModeController.toggle(context, false, AutoControlMode.SILENT))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test fun changingModeRestoresOldModeBeforeApplyingNewMode() {
        assertTrue(AutoModeController.toggle(context, true, AutoControlMode.DND))
        assertTrue(AutoModeController.toggle(context, true, AutoControlMode.SILENT))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, notification.currentInterruptionFilter)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertTrue(AutoModeController.toggle(context, false, AutoControlMode.SILENT))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
    }

    @Test fun disablingAfterUserChangedModePreservesTheUserChange() {
        assertTrue(AutoModeController.toggle(context, true, AutoControlMode.DND))
        notification.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertTrue(AutoModeController.toggle(context, false, AutoControlMode.DND))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, notification.currentInterruptionFilter)
    }
}
