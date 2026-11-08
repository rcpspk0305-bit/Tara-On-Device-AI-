package dev.voice.offline.actions.notifications

import android.content.Context
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.core.system.ReplyNotificationListener

class NotificationActionExecutor(private val context: Context) {

    fun readNotifications(): ExecutionResult {
        if (!ReplyNotificationListener.isConnected()) {
            return ExecutionResult.Failed("Notification listener access is not connected or enabled in Android settings")
        }

        val notifications = ReplyNotificationListener.getRecentNotifications()
        if (notifications.isEmpty()) {
            return ExecutionResult.Success("You have no active unread notifications")
        }

        val formatted = notifications.take(5).joinToString("; ") { (sender, text) ->
            "$sender: $text"
        }
        return ExecutionResult.Success("Recent notifications: $formatted")
    }

    fun replyToNotification(action: StructuredAction.ReplyToNotification): ExecutionResult {
        if (!ReplyNotificationListener.isConnected()) {
            return ExecutionResult.Failed("Notification listener service is not running")
        }

        val success = ReplyNotificationListener.replyTo(action.notificationId, action.body)
        return if (success) {
            ExecutionResult.Success("Reply submitted to notification from ${action.notificationId}")
        } else {
            ExecutionResult.Failed("No matching replyable notification found for '${action.notificationId}'")
        }
    }
}
