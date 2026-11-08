package dev.voice.offline.actions.notes

import android.content.Context
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.core.storage.PrivateNoteStore

class NoteActionExecutor(private val context: Context) {

    fun takeNotes(action: StructuredAction.TakeNotes): ExecutionResult {
        return try {
            val store = PrivateNoteStore(context)
            store.insert(action.content)
            ExecutionResult.Success("Encrypted note saved locally")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to securely save note: ${e.message}")
        }
    }
}
