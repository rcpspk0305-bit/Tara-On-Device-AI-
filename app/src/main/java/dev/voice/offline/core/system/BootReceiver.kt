package dev.voice.offline.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.voice.offline.service.VoiceAssistantService

/**
 * Automatically restores hands-free "Hey Tara" listening on device boot
 * if the user has enabled hands-free mode in settings.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS_NAME = "voice_app_prefs"
        const val KEY_HANDS_FREE_ENABLED = "hands_free_enabled"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val handsFree = prefs.getBoolean(KEY_HANDS_FREE_ENABLED, false)

        if (handsFree) {
            val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                action = VoiceAssistantService.ACTION_HANDS_FREE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to restore hands-free service on boot: ${e.message}")
            }
        }
    }
}

