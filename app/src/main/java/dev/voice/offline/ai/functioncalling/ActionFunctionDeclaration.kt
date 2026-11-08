package dev.voice.offline.ai.functioncalling

/**
 * Predefined FunctionGemma 270M tool declarations adhering to the official
 * Mobile Actions format and Gemma chat template control tokens.
 */
object ActionFunctionDeclaration {

    val ALL_DECLARATIONS = listOf(
        declaration(
            name = "make_call",
            description = "Make a phone call to the specified contact",
            properties = listOf("contact_name" to "STRING"),
            required = listOf("contact_name")
        ),
        declaration(
            name = "answer_call",
            description = "Answer the currently ringing incoming phone call",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "reject_call",
            description = "Reject or decline the incoming phone call",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "open_camera",
            description = "Open the device camera application",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "lock_screen",
            description = "Immediately lock the device screen",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "take_notes",
            description = "Save a secure text note locally on the device",
            properties = listOf("content" to "STRING"),
            required = listOf("content")
        ),
        declaration(
            name = "open_app",
            description = "Launch an application by its package name or application name",
            properties = listOf("package_name" to "STRING"),
            required = listOf("package_name")
        ),
        declaration(
            name = "set_alarm",
            description = "Set a local clock alarm with specified hour, minute, and optional label",
            properties = listOf("hour" to "INTEGER", "minute" to "INTEGER", "label" to "STRING"),
            required = listOf("hour", "minute")
        ),
        declaration(
            name = "turn_flashlight_on",
            description = "Turn on the device flashlight or torch",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "turn_flashlight_off",
            description = "Turn off the device flashlight or torch",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "text_msg",
            description = "Compose or send a text message to a contact",
            properties = listOf("contact_name" to "STRING", "body" to "STRING"),
            required = listOf("contact_name", "body")
        ),
        declaration(
            name = "read_notifications",
            description = "Read out recent unread notifications",
            properties = emptyList(),
            required = emptyList()
        ),
        declaration(
            name = "reply_to_notification",
            description = "Reply to an active notification with a specified message body",
            properties = listOf("notification_id" to "STRING", "body" to "STRING"),
            required = listOf("notification_id", "body")
        )
    )

    private fun declaration(
        name: String,
        description: String,
        properties: List<Pair<String, String>>,
        required: List<String>
    ): String {
        val propsString = if (properties.isEmpty()) {
            ""
        } else {
            "properties:{" + properties.joinToString(",") { (key, type) ->
                "$key:{type:<escape>$type<escape>}"
            } + "},"
        }
        val reqString = if (required.isEmpty()) {
            ""
        } else {
            "required:[" + required.joinToString(",") { "<escape>$it<escape>" } + "],"
        }
        return "<start_function_declaration>declaration:$name{description:<escape>$description<escape>,parameters:{$propsString$reqString" +
                "type:<escape>OBJECT<escape>}}<end_function_declaration>"
    }
}
