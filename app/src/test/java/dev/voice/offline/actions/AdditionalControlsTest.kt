package dev.voice.offline.actions

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import dev.voice.offline.actions.navigation.NavigationActionExecutor
import dev.voice.offline.actions.settings.SettingsActionExecutor
import dev.voice.offline.actions.volume.VolumeActionExecutor
import dev.voice.offline.ai.functioncalling.FunctionCallParser
import dev.voice.offline.ai.functioncalling.FunctionSchemaValidator
import dev.voice.offline.ai.functioncalling.RawFunctionCall
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.ai.functioncalling.ValidationResult
import org.junit.Assert.*
import org.junit.Test

class AdditionalControlsTest {

    @Test
    fun testSettingsActionExecutorLaunchesCorrectIntents() {
        var launchedAction: String? = null
        val executor = SettingsActionExecutor(settingsLauncher = { launchedAction = it })

        val wifiRes = executor.openSettings(StructuredAction.OpenSettings("wifi"))
        assertTrue(wifiRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, launchedAction)

        val btRes = executor.openSettings(StructuredAction.OpenSettings("bluetooth"))
        assertTrue(btRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, launchedAction)

        val soundRes = executor.openSettings(StructuredAction.OpenSettings("sound"))
        assertTrue(soundRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_SOUND_SETTINGS, launchedAction)

        val displayRes = executor.openSettings(StructuredAction.OpenSettings("display"))
        assertTrue(displayRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, launchedAction)

        val genRes = executor.openSettings(StructuredAction.OpenSettings("general"))
        assertTrue(genRes is ExecutionResult.Success)
        assertEquals(Settings.ACTION_SETTINGS, launchedAction)
    }

    @Test
    fun testVolumeActionExecutorAdjustsVolumeProperly() {
        var lastStream = -1
        var lastDirection = -999
        var lastVolumeSet = -1

        val executor = VolumeActionExecutor(
            adjustStream = { stream, dir, _ ->
                lastStream = stream
                lastDirection = dir
            },
            setStream = { stream, index, _ ->
                lastStream = stream
                lastVolumeSet = index
            },
            maxVolume = { 15 }
        )

        // Volume Up
        val upRes = executor.adjustVolume(StructuredAction.AdjustVolume("up"))
        assertTrue(upRes is ExecutionResult.Success)
        assertEquals(AudioManager.STREAM_MUSIC, lastStream)
        assertEquals(AudioManager.ADJUST_RAISE, lastDirection)

        // Volume Down
        val downRes = executor.adjustVolume(StructuredAction.AdjustVolume("down"))
        assertTrue(downRes is ExecutionResult.Success)
        assertEquals(AudioManager.ADJUST_LOWER, lastDirection)

        // Volume Mute
        val muteRes = executor.adjustVolume(StructuredAction.AdjustVolume("mute"))
        assertTrue(muteRes is ExecutionResult.Success)
        assertEquals(AudioManager.ADJUST_MUTE, lastDirection)

        // Set Volume Percentage (e.g. 50% of 15 -> ~8)
        val setRes = executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = 50))
        assertTrue(setRes is ExecutionResult.Success)
        assertEquals(AudioManager.STREAM_MUSIC, lastStream)
        assertTrue("Index should be around 7 or 8", lastVolumeSet in 7..8)
    }

    @Test
    fun testNavigationActionExecutorDispatchesHomeCategory() {
        var homeLaunched = false
        val executor = NavigationActionExecutor(homeLauncher = { homeLaunched = true })

        val result = executor.navigateHome()
        assertTrue(result is ExecutionResult.Success)
        assertTrue("Home navigation must be triggered", homeLaunched)
    }

    @Test
    fun testSchemaValidationForAdditionalControls() {
        // Open Settings
        val settingsRaw = RawFunctionCall("open_settings", mapOf("setting_type" to "wifi"))
        val settingsVal = FunctionSchemaValidator.validate(settingsRaw)
        assertTrue(settingsVal is ValidationResult.Valid)
        val settingsAction = (settingsVal as ValidationResult.Valid).action
        assertTrue(settingsAction is StructuredAction.OpenSettings)
        assertEquals("wifi", (settingsAction as StructuredAction.OpenSettings).settingType)

        // Adjust Volume
        val volumeRaw = RawFunctionCall("adjust_volume", mapOf("direction" to "up"))
        val volumeVal = FunctionSchemaValidator.validate(volumeRaw)
        assertTrue(volumeVal is ValidationResult.Valid)
        val volumeAction = (volumeVal as ValidationResult.Valid).action
        assertTrue(volumeAction is StructuredAction.AdjustVolume)
        assertEquals("up", (volumeAction as StructuredAction.AdjustVolume).direction)

        // Navigate Home
        val homeRaw = RawFunctionCall("navigate_home", emptyMap())
        val homeVal = FunctionSchemaValidator.validate(homeRaw)
        assertTrue(homeVal is ValidationResult.Valid)
        assertEquals(StructuredAction.NavigateHome, (homeVal as ValidationResult.Valid).action)
    }
}
