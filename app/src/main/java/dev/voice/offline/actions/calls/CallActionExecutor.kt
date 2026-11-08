package dev.voice.offline.actions.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction

class CallActionExecutor(private val context: Context) {

    fun makeCall(action: StructuredAction.MakeCall): ExecutionResult {
        val contactName = action.contactName.trim()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return ExecutionResult.RequiresPermission(
                Manifest.permission.READ_CONTACTS,
                "Read contacts permission required to look up $contactName"
            )
        }

        val resolution = resolveContact(contactName)
        if (resolution.candidates.isEmpty()) {
            return ExecutionResult.Failed("No contact found matching '$contactName'")
        }
        if (resolution.candidates.size > 1) {
            return ExecutionResult.Ambiguous(
                resolution.candidates,
                "Multiple matching contacts found for '$contactName': ${resolution.candidates.joinToString()}"
            )
        }

        val phoneNumber = resolution.phoneNumbers.firstOrNull()
            ?: return ExecutionResult.Failed("No phone number recorded for '$contactName'")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            // Safe fallback to dialer without direct calling
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dialIntent)
            return ExecutionResult.Success("Dialer opened with $contactName ($phoneNumber)")
        }

        val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phoneNumber, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(callIntent)
        return ExecutionResult.Success("Calling $contactName")
    }

    fun answerCall(): ExecutionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED) {
            return ExecutionResult.RequiresPermission(
                Manifest.permission.ANSWER_PHONE_CALLS,
                "Permission required to answer phone calls"
            )
        }

        val telecom = context.getSystemService(TelecomManager::class.java)
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
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return ExecutionResult.Failed("Telecom service unavailable")

        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED) {
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

    private data class ContactResolution(
        val candidates: List<String>,
        val phoneNumbers: List<String>
    )

    private fun resolveContact(query: String): ContactResolution {
        val candidates = mutableListOf<String>()
        val numbers = mutableListOf<String>()
        val projection = arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER)

        // Exact match first
        val exactSelection = "${Phone.DISPLAY_NAME_PRIMARY} = ?"
        context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            exactSelection,
            arrayOf(query),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val num = cursor.getString(1) ?: continue
                if (!candidates.contains(name)) candidates.add(name)
                if (!numbers.contains(num)) numbers.add(num)
            }
        }

        if (candidates.isNotEmpty()) {
            return ContactResolution(candidates, numbers)
        }

        // Fuzzy/prefix match
        val likeSelection = "${Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            likeSelection,
            arrayOf("%$query%"),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val num = cursor.getString(1) ?: continue
                if (!candidates.contains(name)) candidates.add(name)
                if (!numbers.contains(num)) numbers.add(num)
            }
        }

        return ContactResolution(candidates, numbers)
    }
}
