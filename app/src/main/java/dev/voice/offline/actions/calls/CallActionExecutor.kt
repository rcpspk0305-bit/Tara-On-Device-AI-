package dev.voice.offline.actions.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.contacts.ContactResolution
import dev.voice.offline.contacts.ContactResolver

class CallActionExecutor(
    private val context: Context? = null,
    private val resolver: ContactResolver = ContactResolver(context),
    private val hasContactsPermission: () -> Boolean = {
        context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    },
    private val hasCallPhonePermission: () -> Boolean = {
        context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    },
    private val hasAnswerPermission: () -> Boolean = {
        context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
    },
    private val callLauncher: (action: String, uriString: String) -> Unit = { action, uriString ->
        val intent = Intent(action, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context?.startActivity(intent)
    }
) {
    fun makeCall(action: StructuredAction.MakeCall): ExecutionResult {
        val rawTarget = action.contactName.trim()
        if (rawTarget.isEmpty()) {
            return ExecutionResult.Failed("No contact name or phone number specified")
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
                    "Read contacts permission required to look up $rawTarget"
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
                    return ExecutionResult.Failed("No contact found matching '$rawTarget'")
                }
            }
        }

        if (!hasCallPhonePermission()) {
            // Permission-light fallback to dialer without placing the call automatically
            callLauncher(Intent.ACTION_DIAL, "tel:$phoneNumber")
            return ExecutionResult.Success("Dialer opened with $displayName ($phoneNumber)")
        }

        callLauncher(Intent.ACTION_CALL, "tel:$phoneNumber")
        return ExecutionResult.Success("Calling $displayName")
    }

    fun answerCall(): ExecutionResult {
        if (!hasAnswerPermission()) {
            return ExecutionResult.RequiresPermission(
                Manifest.permission.ANSWER_PHONE_CALLS,
                "Permission required to answer phone calls"
            )
        }

        val telecom = context?.getSystemService(TelecomManager::class.java)
            ?: return ExecutionResult.Failed("Telecom service unavailable")

        return try {
            @Suppress("DEPRECATION")
            telecom.acceptRingingCall()
            ExecutionResult.Success("Call answered")
        } catch (e: SecurityException) {
            ExecutionResult.Failed("System policy denied answering call: ${e.message}")
        } catch (e: Exception) {
            ExecutionResult.Failed("Unable to answer call: ${e.message}")
        }
    }

    fun rejectCall(): ExecutionResult {
        val telecom = context?.getSystemService(TelecomManager::class.java)
            ?: return ExecutionResult.Failed("Telecom service unavailable")

        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (hasAnswerPermission()) {
                    @Suppress("DEPRECATION")
                    telecom.endCall()
                    ExecutionResult.Success("Call rejected")
                } else {
                    ExecutionResult.RequiresPermission(
                        Manifest.permission.ANSWER_PHONE_CALLS,
                        "Permission required to reject incoming call"
                    )
                }
            } else {
                ExecutionResult.Unsupported("Rejecting calls is not supported on this Android version")
            }
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to reject call: ${e.message}")
        }
    }
}
