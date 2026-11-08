package dev.voice.offline.core.system

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService

/** Reply only to an existing, unambiguously identified notification containing a reply action. */
class ReplyNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile private var connected: ReplyNotificationListener? = null

        fun replyTo(sender: String, message: String): Boolean {
            val listener = connected ?: return false
            val matches = listener.activeNotifications.orEmpty().mapNotNull { entry ->
                val title = entry.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                    ?.toString()?.trim() ?: return@mapNotNull null
                if (!title.equals(sender, ignoreCase = true)) return@mapNotNull null
                val action = entry.notification.actions?.firstOrNull { action ->
                    action.remoteInputs?.any { it.allowFreeFormInput } == true
                } ?: return@mapNotNull null
                Pair(action, action.remoteInputs.first { it.allowFreeFormInput })
            }
            if (matches.size != 1) return false
            val (action, input) = matches.single()
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, message) }
            RemoteInput.addResultsToIntent(arrayOf(input), intent, results)
            return try {
                action.actionIntent.send(listener, 0, intent)
                true // RemoteInput accepted; remote app delivery cannot be verified here.
            } catch (_: Exception) { false }
        }

        fun isConnected(): Boolean = connected != null

        fun getRecentNotifications(): List<Pair<String, String>> {
            val listener = connected ?: return emptyList()
            return listener.activeNotifications.orEmpty().mapNotNull { entry ->
                val title = entry.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val text = entry.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                if (title != null && text != null) Pair(title, text) else null
            }
        }
    }
    override fun onListenerConnected() { connected = this }
    override fun onListenerDisconnected() { if (connected === this) connected = null }
    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }
}
