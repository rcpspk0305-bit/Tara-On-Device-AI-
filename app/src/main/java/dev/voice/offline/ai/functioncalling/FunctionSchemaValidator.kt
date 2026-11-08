package dev.voice.offline.ai.functioncalling

sealed interface ValidationResult {
    data class Valid(val action: StructuredAction) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Validates raw model output against the registered schema and arguments.
 * Ensures the model cannot execute arbitrary code or shell injection.
 */
object FunctionSchemaValidator {

    private val allowedArguments = mapOf(
        "make_call" to setOf("contact_name"),
        "answer_call" to emptySet(),
        "reject_call" to emptySet(),
        "open_camera" to emptySet(),
        "lock_screen" to emptySet(),
        "take_notes" to setOf("content"),
        "open_app" to setOf("package_name"),
        "set_alarm" to setOf("hour", "minute", "label"),
        "turn_flashlight_on" to emptySet(),
        "turn_flashlight_off" to emptySet(),
        "text_msg" to setOf("contact_name", "body"),
        "read_notifications" to emptySet(),
        "reply_to_notification" to setOf("notification_id", "body")
    )

    fun validate(raw: RawFunctionCall): ValidationResult {
        val allowed = allowedArguments[raw.name]
            ?: return ValidationResult.Invalid("Unsupported or unknown function: ${raw.name}")
        if (raw.arguments.keys.any { it !in allowed }) {
            return ValidationResult.Invalid("Undeclared argument for ${raw.name}")
        }
        return when (raw.name) {
            "make_call" -> {
                val contact = raw.arguments["contact_name"]?.trim()
                if (contact.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: contact_name")
                } else if (contact.length > 70) {
                    ValidationResult.Invalid("contact_name exceeds maximum length")
                } else {
                    ValidationResult.Valid(StructuredAction.MakeCall(contact))
                }
            }

            "answer_call" -> {
                ValidationResult.Valid(StructuredAction.AnswerCall)
            }

            "reject_call" -> {
                ValidationResult.Valid(StructuredAction.RejectCall)
            }

            "open_camera" -> {
                ValidationResult.Valid(StructuredAction.OpenCamera)
            }

            "lock_screen" -> {
                ValidationResult.Valid(StructuredAction.LockScreen)
            }

            "take_notes" -> {
                val content = raw.arguments["content"]?.trim()
                if (content.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: content")
                } else if (content.length > 2000) {
                    ValidationResult.Invalid("content exceeds maximum 2000 characters")
                } else {
                    ValidationResult.Valid(StructuredAction.TakeNotes(content))
                }
            }

            "open_app" -> {
                val pkg = raw.arguments["package_name"]?.trim()
                if (pkg.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: package_name")
                } else {
                    ValidationResult.Valid(StructuredAction.OpenApp(pkg))
                }
            }

            "set_alarm" -> {
                val hourStr = raw.arguments["hour"]
                val minuteStr = raw.arguments["minute"]
                val label = raw.arguments["label"]?.trim() ?: "Alarm"

                if (hourStr == null || minuteStr == null) {
                    return ValidationResult.Invalid("Missing required arguments for set_alarm: hour and minute")
                }

                val hour = hourStr.toIntOrNull()
                val minute = minuteStr.toIntOrNull()

                if (hour == null || hour !in 0..23) {
                    ValidationResult.Invalid("Invalid hour for alarm: $hourStr (must be 0-23)")
                } else if (minute == null || minute !in 0..59) {
                    ValidationResult.Invalid("Invalid minute for alarm: $minuteStr (must be 0-59)")
                } else {
                    ValidationResult.Valid(StructuredAction.SetAlarm(hour, minute, label))
                }
            }

            "turn_flashlight_on" -> {
                ValidationResult.Valid(StructuredAction.TurnFlashlightOn)
            }

            "turn_flashlight_off" -> {
                ValidationResult.Valid(StructuredAction.TurnFlashlightOff)
            }

            "text_msg" -> {
                val contact = raw.arguments["contact_name"]?.trim()
                val body = raw.arguments["body"]?.trim()

                if (contact.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: contact_name")
                } else if (body.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: body")
                } else {
                    ValidationResult.Valid(StructuredAction.TextMsg(contact, body))
                }
            }

            "read_notifications" -> {
                ValidationResult.Valid(StructuredAction.ReadNotifications)
            }

            "reply_to_notification" -> {
                val notificationId = raw.arguments["notification_id"]?.trim()
                val body = raw.arguments["body"]?.trim()

                if (notificationId.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: notification_id")
                } else if (body.isNullOrBlank()) {
                    ValidationResult.Invalid("Missing required argument: body")
                } else {
                    ValidationResult.Valid(StructuredAction.ReplyToNotification(notificationId, body))
                }
            }

            else -> {
                ValidationResult.Invalid("Unsupported or unknown function: ${raw.name}")
            }
        }
    }
}
