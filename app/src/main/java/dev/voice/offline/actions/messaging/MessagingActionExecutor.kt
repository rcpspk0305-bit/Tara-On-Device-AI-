package dev.voice.offline.actions.messaging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction

class MessagingActionExecutor(private val context: Context) {

    fun sendText(action: StructuredAction.TextMsg): ExecutionResult {
        val contactName = action.contactName.trim()
        val body = action.body.trim()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return ExecutionResult.RequiresPermission(
                Manifest.permission.READ_CONTACTS,
                "Permission required to look up $contactName"
            )
        }

        val phoneNumber = resolvePhoneNumber(contactName)
            ?: return ExecutionResult.Failed("Could not resolve phone number for '$contactName'")

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(smsIntent)
            ExecutionResult.Success("Draft message prepared for $contactName")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to launch messaging app: ${e.message}")
        }
    }

    private fun resolvePhoneNumber(name: String): String? {
        val projection = arrayOf(Phone.NUMBER)
        val selection = "${Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf("%$name%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
}
