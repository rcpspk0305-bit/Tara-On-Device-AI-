package dev.tara.privacy

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tara.designsystem.TaraCard
import dev.tara.designsystem.TaraPage
import dev.tara.domain.Capability
import dev.tara.domain.PermissionStatus

@Composable
fun PrivacyScreen(statuses: Map<Capability, PermissionStatus>, onPermission: (Capability) -> Unit) {
    TaraPage("Your privacy", "A small permission footprint. A clear view of your controls.") {
        TaraCard {
            Text("What stays local", style = MaterialTheme.typography.titleLarge)
            Text("Phase 1 has no network permission. Tara does not upload or persist recordings, transcripts, message contents, contact numbers or your app inventory.")
            Text("Only appearance, onboarding progress and permission-request history are stored. Android recognition availability depends on your device and downloaded language support.")
            Text("Calls, messaging and clock handoffs open another app. That app handles the final action under its own permissions and privacy policy.")
        }
        Capability.entries.forEach { capability ->
            val status = statuses[capability] ?: PermissionStatus.UNAVAILABLE
            TaraCard {
                Text(capability.title(), style = MaterialTheme.typography.titleLarge)
                Text("Status: ${status.name.lowercase().replace('_', ' ')}")
                Text(capability.explanation())
                OutlinedButton(onClick = { onPermission(capability) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage ${capability.title().lowercase()}") }
            }
        }
        TaraCard {
            Text("No broad access", style = MaterialTheme.typography.titleLarge)
            Text("No SEND_SMS, READ_SMS, call logs, address-book permission, all-packages visibility, Accessibility service, device-owner control or background microphone service is used in Phase 1.")
            Text("To uninstall while device administrator is enabled, revoke Tara’s administrator access first in Android settings.")
        }
    }
}

private fun Capability.title() = when (this) {
    Capability.MICROPHONE -> "Microphone"
    Capability.CAMERA -> "Flashlight camera access"
    Capability.DEVICE_ADMIN -> "Device administrator"
    Capability.SECURE_LOCK -> "Secure screen lock"
}
private fun Capability.explanation() = when (this) {
    Capability.MICROPHONE -> "Used only when you start listening. Leaving the assistant stops capture."
    Capability.CAMERA -> "Android requires camera access for torch control. Tara does not take photos or videos."
    Capability.DEVICE_ADMIN -> "Used only for the force-lock policy after explicit Android approval."
    Capability.SECURE_LOCK -> "A PIN, pattern or password protects your device when it is locked."
}
