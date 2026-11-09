package dev.voice.offline.actions.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction

/**
 * Executes system settings navigation commands via standard Android intents.
 */
class SettingsActionExecutor(
    private val context: Context? = null,
    private val settingsLauncher: (actionName: String) -> Unit = { actionName ->
        val intent = Intent(actionName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context?.startActivity(intent)
    }
) {
    fun openSettings(action: StructuredAction.OpenSettings): ExecutionResult {
        val type = action.settingType.lowercase().trim()
        val actionName = when (type) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            settingsLauncher(actionName)
            ExecutionResult.Success("Opening $type settings")
        } catch (e: Exception) {
            ExecutionResult.Failed("Could not open settings: ${e.message}")
        }
    }
}
