package dev.voice.offline.ai.functioncalling

import org.json.JSONObject

/**
 * Strict, versioned representations of actions supported by Voice.
 * Follows the FunctionGemma 270M Mobile Actions vocabulary.
 */
sealed interface StructuredAction {
    val functionName: String

    data class MakeCall(val contactName: String) : StructuredAction {
        override val functionName: String = "make_call"
    }

    data object AnswerCall : StructuredAction {
        override val functionName: String = "answer_call"
    }

    data object RejectCall : StructuredAction {
        override val functionName: String = "reject_call"
    }

    data object OpenCamera : StructuredAction {
        override val functionName: String = "open_camera"
    }

    data object LockScreen : StructuredAction {
        override val functionName: String = "lock_screen"
    }

    data class TakeNotes(val content: String) : StructuredAction {
        override val functionName: String = "take_notes"
    }

    data class OpenApp(val packageName: String) : StructuredAction {
        override val functionName: String = "open_app"
    }

    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Alarm") : StructuredAction {
        override val functionName: String = "set_alarm"
    }

    data object TurnFlashlightOn : StructuredAction {
        override val functionName: String = "turn_flashlight_on"
    }

    data object TurnFlashlightOff : StructuredAction {
        override val functionName: String = "turn_flashlight_off"
    }

    data class TextMsg(val contactName: String, val body: String) : StructuredAction {
        override val functionName: String = "text_msg"
    }

    data object ReadNotifications : StructuredAction {
        override val functionName: String = "read_notifications"
    }

    data class ReplyToNotification(val notificationId: String, val body: String) : StructuredAction {
        override val functionName: String = "reply_to_notification"
    }

    fun toNormalizedJson(): String {
        val root = JSONObject()
        root.put("schema_version", 1)
        root.put("function", functionName)

        val args = JSONObject()
        when (this) {
            is MakeCall -> args.put("contact_name", contactName)
            is AnswerCall -> Unit
            is RejectCall -> Unit
            is OpenCamera -> Unit
            is LockScreen -> Unit
            is TakeNotes -> args.put("content", content)
            is OpenApp -> args.put("package_name", packageName)
            is SetAlarm -> {
                args.put("hour", hour)
                args.put("minute", minute)
                args.put("label", label)
            }
            is TurnFlashlightOn -> Unit
            is TurnFlashlightOff -> Unit
            is TextMsg -> {
                args.put("contact_name", contactName)
                args.put("body", body)
            }
            is ReadNotifications -> Unit
            is ReplyToNotification -> {
                args.put("notification_id", notificationId)
                args.put("body", body)
            }
        }
        root.put("arguments", args)
        return root.toString()
    }
}
