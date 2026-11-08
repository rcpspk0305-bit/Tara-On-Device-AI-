package dev.voice.offline.core.system

import dev.voice.offline.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.voice.offline.service.VoiceAssistantService

/**
 * Automatically restores hands-free "Hey Tara" listening on device boot
 * if the user has enabled hands-free mode in settings.
 *
 * On Android 14+ (API 34+), background broadcast receivers are restricted from
 * launching microphone foreground services directly. A user-actionable notification
 * is posted so the user can resume hands-free listening with a single tap.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS_NAME = "voice_app_prefs"
        const val KEY_HANDS_FREE_ENABLED = "hands_free_enabled"
        private const val CHANNEL_RESUME = "voice_resume"
        private const val NOTIFICATION_ID_BOOT = 1001
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
                if (Build.VERSION.SDK_INT >= 34) {
                    // Android 14+ microphone foreground service restriction
                    postResumeNotification(context, serviceIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to restore hands-free service on boot: ${e.message}")
            }
        }
    }

    private fun postResumeNotification(context: Context, serviceIntent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_RESUME,
                "Assistant Resume",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts to resume hands-free voice control after reboot"
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_ID_BOOT,
            serviceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_RESUME)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.apply {
            setSmallIcon(R.drawable.ic_mic)
            setContentTitle("Tara Hands-Free Active")
            setContentText("Tap to resume listening for \"Hey Tara\"")
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setShowWhen(true)
        }.build()

        manager.notify(NOTIFICATION_ID_BOOT, notification)
    }
}

