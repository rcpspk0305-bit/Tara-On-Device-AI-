package dev.voice.offline.actions.messaging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.contacts.ContactResolution
import dev.voice.offline.contacts.ContactResolver

/**
 * Executes messaging voice commands offline.
 * Resolves contacts, constructs explicit readback for TTS confirmation,
 * and launches ACTION_SENDTO to let the user review and complete sending.
 * Never silently transmits SMS data without user interaction.
 */
class MessagingActionExecutor(
    private val context: Context? = null,
    private val resolver: ContactResolver = ContactResolver(context),
    private val hasContactsPermission: () -> Boolean = {
        context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    },
    private val smsLauncher: (phoneNumber: String, body: String) -> Unit = { phoneNumber, body ->
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context?.startActivity(smsIntent)
    }
) {
    fun sendText(action: StructuredAction.TextMsg): ExecutionResult {
        val rawTarget = action.contactName.trim()
        val body = action.body.trim()

        if (rawTarget.isEmpty()) {
            return ExecutionResult.Failed("No recipient specified for message")
        }
        if (body.isEmpty()) {
            return ExecutionResult.Failed("Message body cannot be empty")
        }

        // Fast path for raw phone numbers - no contacts permission needed
        val isDirectDigits = rawTarget.filter { it.isDigit() }.length >= 3 &&
                rawTarget.all { it.isDigit() || it in "+ -()." }

        val (displayName, phoneNumber) = if (isDirectDigits) {
            val cleanNumber = (if (rawTarget.startsWith("+")) "+" else "") + rawTarget.filter { it.isDigit() }
            Pair(cleanNumber, cleanNumber)
        } else {
            if (!hasContactsPermission()) {
                return ExecutionResult.RequiresPermission(
                    Manifest.permission.READ_CONTACTS,
                    "Permission required to look up $rawTarget"
                )
            }
            when (val res = resolver.resolve(rawTarget)) {
                is ContactResolution.DirectNumber -> Pair(res.phoneNumber, res.phoneNumber)
                is ContactResolution.Resolved -> Pair(res.contact.displayName, res.selectedNumber)
                is ContactResolution.Ambiguous -> {
                    val names = res.candidates.map { it.displayName }
                    return ExecutionResult.Ambiguous(
                        names,
                        "Multiple matching contacts found for '$rawTarget': ${names.joinToString()}"
                    )
                }
                is ContactResolution.NotFound -> {
                    return ExecutionResult.Failed("Could not resolve phone number for '$rawTarget'")
                }
            }
        }

        return try {
            smsLauncher(phoneNumber, body)
            ExecutionResult.Success("Draft message to $displayName: \"$body\". Please review and tap send in Messages.")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to launch messaging app: ${e.message}")
        }
    }
}
