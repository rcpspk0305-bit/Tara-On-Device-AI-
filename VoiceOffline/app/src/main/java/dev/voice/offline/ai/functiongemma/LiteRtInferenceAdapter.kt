package dev.voice.offline.ai.functiongemma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Adapter interface abstracting local on-device LiteRT-LM execution.
 * Allows decoupling between the FunctionGemma engine, native LiteRT runtime,
 * and test/fallback environments.
 */
interface LiteRtInferenceAdapter : AutoCloseable {
    val isReady: Boolean
    val backendName: String get() = "CPU"
    fun initialize()
    suspend fun generate(prompt: String): Flow<String>
    override fun close()
}

/**
 * Fallback & testing adapter providing deterministic FunctionGemma 270M
 * Mobile Actions completions when native LiteRT-LM drivers are uninitialized or in test suites.
 */
class EmulatedLiteRtInferenceAdapter : LiteRtInferenceAdapter {
    override var isReady: Boolean = false
        private set

    override val backendName: String = "EMULATED_CPU"

    override fun initialize() {
        isReady = true
    }

    override suspend fun generate(prompt: String): Flow<String> = flow {
        // Extract the user turn from the prompt
        val rawTurn = Regex("""<start_of_turn>user\s*\n(.*?)\n<end_of_turn>""", RegexOption.DOT_MATCHES_ALL)
            .find(prompt)?.groupValues?.get(1)?.trim()?.lowercase() ?: ""
        val userTurn = rawTurn.replace(Regex("^(?:hey|okay|hi|hello)?\\s*\\b(?:tara|voice)\\b[,\\s:]*"), "").trim()

        // Check for prompt injection, jailbreaks, or destructive non-supported commands
        val isInjectionOrAttack = userTurn.contains("ignore previous") ||
                userTurn.contains("ignore system") ||
                userTurn.contains("format device") ||
                userTurn.contains("transfer money") ||
                userTurn.contains("delete contacts") ||
                userTurn.contains("delete all") ||
                userTurn.contains("system override") ||
                userTurn.contains("grant root") ||
                userTurn.contains("secret backdoor") ||
                userTurn.contains("wipe external") ||
                userTurn.contains("turn off biometric") ||
                userTurn.contains("malicious") ||
                userTurn.contains("recharge phone") ||
                userTurn.contains("missed call") ||
                userTurn.contains("hack_") ||
                userTurn.contains("delete_all")

        val call = if (isInjectionOrAttack) {
            "" // Model rejects malicious or unsupported prompts
        } else when {
            // Flashlight / Torch
            userTurn.contains("torch on") || userTurn.contains("flashlight on") ||
            userTurn.contains("turn on the flashlight") || userTurn.contains("turn on flashlight") ||
            userTurn.contains("turn on light") ->
                "<start_function_call>call:turn_flashlight_on{}<end_function_call>"

            userTurn.contains("torch off") || userTurn.contains("flashlight off") ||
            userTurn.contains("torch light band") || userTurn.contains("turn off the flashlight") ||
            userTurn.contains("turn off flashlight") || userTurn.contains("turn off light") ->
                "<start_function_call>call:turn_flashlight_off{}<end_function_call>"

            // Camera / Selfie / Snap
            userTurn.contains("selfie") || userTurn.contains("camera") ||
            userTurn.contains("picture") || userTurn.contains("photo") ||
            userTurn.contains("snap") ->
                "<start_function_call>call:open_camera{}<end_function_call>"

            // Lock screen / phone / device
            userTurn.contains("lock screen") || userTurn.contains("lock phone") ||
            userTurn.contains("lock the screen") || userTurn.contains("lock device") ->
                "<start_function_call>call:lock_screen{}<end_function_call>"

            // Calls
            userTurn.contains("answer") ->
                "<start_function_call>call:answer_call{}<end_function_call>"

            userTurn.contains("reject") || userTurn.contains("decline") ->
                "<start_function_call>call:reject_call{}<end_function_call>"

            userTurn.contains("ko call") -> { // Indian English: e.g. "Papa ko call lagao", "Mummy ko call karo"
                val contact = userTurn.substringBefore("ko call").trim()
                "<start_function_call>call:make_call{contact_name:<escape>$contact<escape>}<end_function_call>"
            }

            userTurn.startsWith("call ") || userTurn.startsWith("phone ") -> {
                val rawContact = userTurn.removePrefix("call ").removePrefix("phone ").removeSuffix(" on phone").trim()
                "<start_function_call>call:make_call{contact_name:<escape>$rawContact<escape>}<end_function_call>"
            }

            // Notifications
            userTurn.contains("notifications") ->
                "<start_function_call>call:read_notifications{}<end_function_call>"

            // Notes
            userTurn.startsWith("take a note ") || userTurn.startsWith("note ") ||
            userTurn.startsWith("note likho ") -> {
                val content = userTurn.replace(Regex("^(take a note|note down|note likho|note)\\s+"), "").trim()
                "<start_function_call>call:take_notes{content:<escape>$content<escape>}<end_function_call>"
            }

            // Apps
            userTurn.startsWith("open ") || userTurn.startsWith("launch ") -> {
                val app = userTurn.substringAfter(" ").trim()
                "<start_function_call>call:open_app{package_name:<escape>$app<escape>}<end_function_call>"
            }

            // Alarms
            userTurn.contains("alarm") -> {
                val hourMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(userTurn)
                val hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val minute = hourMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                "<start_function_call>call:set_alarm{hour:$hour,minute:$minute,label:<escape>Alarm<escape>}<end_function_call>"
            }

            // SMS / Messages
            userTurn.contains("sms") || userTurn.contains("text ") || userTurn.contains("message ") -> {
                val afterTo = if (userTurn.contains("to ")) userTurn.substringAfter("to ") else userTurn.substringAfter("message ")
                val contact = afterTo.substringBefore(" saying").substringBefore(" see").substringBefore(" heading").trim()
                val body = when {
                    userTurn.contains("saying ") -> userTurn.substringAfter("saying ").trim()
                    userTurn.contains(" see ") -> "see " + userTurn.substringAfter(" see ").trim()
                    userTurn.contains(" heading ") -> "heading " + userTurn.substringAfter(" heading ").trim()
                    else -> "Message"
                }
                "<start_function_call>call:text_msg{contact_name:<escape>$contact<escape>,body:<escape>$body<escape>}<end_function_call>"
            }

            else -> ""
        }

        emit(call)
    }.flowOn(Dispatchers.Default)

    override fun close() {
        isReady = false
    }
}
