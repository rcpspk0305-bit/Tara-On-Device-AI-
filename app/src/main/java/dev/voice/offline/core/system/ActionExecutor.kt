package dev.voice.offline.core.system

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dev.voice.offline.ai.commands.VoiceCommand
import dev.voice.offline.core.storage.PrivateNoteStore

data class ActionResult(val accepted: Boolean, val message: String)

class ActionExecutor(private val context: Context) {
    fun execute(command: VoiceCommand): ActionResult = try {
        when (command) {
            is VoiceCommand.Note -> {
                PrivateNoteStore(context).use { it.insert(command.content) }
                ActionResult(true, "Note saved locally")
            }
            VoiceCommand.LockScreen -> {
                val policy = context.getSystemService(DevicePolicyManager::class.java)
                val admin = ComponentName(context, VoiceAdminReceiver::class.java)
                if (!policy.isAdminActive(admin)) ActionResult(false, "Enable device admin for screen locking")
                else { policy.lockNow(); ActionResult(true, "Screen locked") }
            }
            VoiceCommand.OpenCamera -> {
                val camera = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(camera)
                ActionResult(true, "Camera opened")
            }
            VoiceCommand.AnswerCall -> {
                requirePermission(Manifest.permission.ANSWER_PHONE_CALLS)
                @Suppress("DEPRECATION")
                context.getSystemService(TelecomManager::class.java).acceptRingingCall()
                ActionResult(true, "Call answer requested")
            }
            is VoiceCommand.Call -> {
                requirePermission(Manifest.permission.CALL_PHONE)
                requirePermission(Manifest.permission.READ_CONTACTS)
                val number = resolveUniquePhone(command.contact)
                context.startActivity(Intent(Intent.ACTION_CALL,
                    Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult(true, "Calling ${command.contact}")
            }
            is VoiceCommand.Reply -> {
                if (ReplyNotificationListener.replyTo(command.sender, command.message))
                    ActionResult(true, "Reply handed to messaging app")
                else ActionResult(false, "No unique matching reply notification")
            }
        }
    } catch (_: SecurityException) { ActionResult(false, "Permission or device policy denied") }
      catch (_: IllegalArgumentException) { ActionResult(false, "Command needs additional authorization or details") }
      catch (_: Exception) { ActionResult(false, "Action could not be completed") }

    private fun requirePermission(name: String) {
        check(ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED) {
            "Permission required: $name"
        }
    }

    private fun resolveUniquePhone(contact: String): String {
        val matches = mutableSetOf<String>()
        val projection = arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER)
        val query = "${Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        context.contentResolver.query(Phone.CONTENT_URI, projection, query, arrayOf(contact), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0)?.trim()?.equals(contact, ignoreCase = true) == true) {
                    cursor.getString(1)?.takeIf { it.isNotBlank() }?.let { matches += it }
                }
            }
        }
        require(matches.size == 1) { "Contact missing or ambiguous" }
        return matches.single()
    }
}
