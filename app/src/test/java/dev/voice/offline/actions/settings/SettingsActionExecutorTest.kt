package dev.voice.offline.actions.settings

import android.provider.Settings
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import org.junit.Assert.*
import org.junit.Test

class SettingsActionExecutorTest {

    @Test
    fun testSettingsActionExecutorDispatchesCorrectIntents() {
        var launchedAction: String? = null
        val executor = SettingsActionExecutor(settingsLauncher = { launchedAction = it })

        // WiFi
        val wifiRes = executor.openSettings(StructuredAction.OpenSettings("wifi"))
        assertTrue(wifiRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, launchedAction)

        // Bluetooth
        val btRes = executor.openSettings(StructuredAction.OpenSettings("bluetooth"))
        assertTrue(btRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, launchedAction)

        // Sound
        val soundRes = executor.openSettings(StructuredAction.OpenSettings("sound"))
        assertTrue(soundRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_SOUND_SETTINGS, launchedAction)

        // Display
        val displayRes = executor.openSettings(StructuredAction.OpenSettings("display"))
        assertTrue(displayRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, launchedAction)

        // Battery
        val batteryRes = executor.openSettings(StructuredAction.OpenSettings("battery"))
        assertTrue(batteryRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_BATTERY_SAVER_SETTINGS, launchedAction)

        // General fallback
        val genRes = executor.openSettings(StructuredAction.OpenSettings("general"))
        assertTrue(genRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_SETTINGS, launchedAction)
    }

    @Test
    fun testSettingsLauncherExceptionHandledGracefully() {
        val executor = SettingsActionExecutor(settingsLauncher = { error("Activity not found") })
        val result = executor.openSettings(StructuredAction.OpenSettings("wifi"))
        assertTrue(result is ExecutionResult.Failed)
        assertTrue(result.message.contains("Could not open settings"))
    }
}

