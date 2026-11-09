package dev.tara.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tara.designsystem.TaraCard
import dev.tara.designsystem.TaraPage
import dev.tara.domain.ActionResult
import dev.tara.domain.AssistantState
import dev.tara.domain.Capability
import dev.tara.domain.SessionState
import dev.tara.domain.preview

@Composable
fun AssistantScreen(
    state: SessionState,
    command: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onChoose: (String) -> Unit,
    onPermission: (Capability) -> Unit,
    onPickContact: () -> Unit,
    debug: Boolean,
) {
    val listening = state.state == AssistantState.Listening
    val busy = state.state in setOf(AssistantState.Listening, AssistantState.Transcribing, AssistantState.Parsing, AssistantState.Resolving, AssistantState.Executing)
    TaraPage("What can I do for you?", "On your device. At your request.") {
        TaraCard {
            Text(state.state.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("assistant_state"))
            Text(state.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("assistant_message"))
            Button(
                onClick = if (listening) onStop else onListen,
                enabled = !busy || listening,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).semantics {
                    contentDescription = if (listening) "Stop microphone and finish recognition" else "Start microphone"
                }.testTag("microphone"),
            ) { Text(if (listening) "Stop listening" else "Tap to speak", style = MaterialTheme.typography.titleLarge) }
            if (busy && !listening) CircularProgressIndicator(Modifier.sizeIn(maxWidth = 28.dp, maxHeight = 28.dp))
            if (busy || state.state == AssistantState.AwaitingConfirmation || state.options.isNotEmpty()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_operation")) { Text("Cancel") }
            }
        }
        if (state.transcript.isNotBlank()) {
            TaraCard {
                Text("Recognized transcript", style = MaterialTheme.typography.titleLarge)
                Text(state.transcript, modifier = Modifier.testTag("transcript"))
            }
        }
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChange,
            enabled = !busy,
            label = { Text("Your command") },
            placeholder = { Text("Try: timer for 5 minutes") },
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("command_input"),
        )
        Button(onClick = onExecute, enabled = command.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("execute")) {
            Text("Execute command")
        }
        OutlinedButton(onClick = onPickContact, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose contact number") }
        state.action?.let { action ->
            TaraCard {
                Text("Action preview", style = MaterialTheme.typography.titleLarge)
                Text(action.preview(), modifier = Modifier.testTag("action_preview"))
            }
        }
        state.options.forEach { option ->
            OutlinedButton(onClick = { onChoose(option.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(option.label) }
        }
        (state.result as? ActionResult.PermissionRequired)?.let { required ->
            TaraCard {
                Text(required.message)
                Button(onClick = { onPermission(required.capability) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review access") }
            }
        }
        Text("Messages open a composer after confirmation. Tara never sends SMS or places a call automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (debug) {
            TaraCard {
                Text("Developer command presets", style = MaterialTheme.typography.titleLarge)
                Text("Presets fill the editor. Review and execute them yourself.")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("open Camera", "volume down", "flashlight on", "timer for 5 minutes", "alarm for 7 am", "sms 9876543210 saying I will be there soon", "lock phone").forEach { preset ->
                        TextButton(onClick = { onCommandChange(preset) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(preset) }
                    }
                }
            }
        }
    }
    val confirmation = state.result as? ActionResult.ConfirmationRequired
    if (state.state == AssistantState.AwaitingConfirmation && confirmation != null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Review before continuing") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(confirmation.action.preview(), modifier = Modifier.testTag("confirmation_details"))
                    Text("This confirmation expires after 30 seconds. Cancel if anything is incorrect.")
                }
            },
            confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_action")) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_confirmation")) { Text("Cancel") } },
        )
    }
}
