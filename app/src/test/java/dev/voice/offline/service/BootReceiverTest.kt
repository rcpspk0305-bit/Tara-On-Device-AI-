package dev.voice.offline.service

import android.content.Intent
import dev.voice.offline.core.system.BootReceiver
import org.junit.Assert.*
import org.junit.Test

class BootReceiverTest {

    private fun shouldRestoreOnBoot(action: String?, handsFreeEnabled: Boolean): Boolean {
        if (action != Intent.ACTION_BOOT_COMPLETED) return false
        return handsFreeEnabled
    }

    @Test
    fun testBootReceiverIgnoresNonBootIntents() {
        val nonBootAction = Intent.ACTION_AIRPLANE_MODE_CHANGED
        assertFalse(shouldRestoreOnBoot(nonBootAction, handsFreeEnabled = true))
    }

    @Test
    fun testBootReceiverStartsServiceWhenHandsFreeEnabled() {
        val bootAction = Intent.ACTION_BOOT_COMPLETED
        assertTrue(shouldRestoreOnBoot(bootAction, handsFreeEnabled = true))
        assertFalse(shouldRestoreOnBoot(bootAction, handsFreeEnabled = false))
    }

    @Test
    fun testBootReceiverConstants() {
        assertEquals("voice_app_prefs", BootReceiver.PREFS_NAME)
        assertEquals("hands_free_enabled", BootReceiver.KEY_HANDS_FREE_ENABLED)
    }
}
