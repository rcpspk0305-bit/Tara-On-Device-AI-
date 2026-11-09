package dev.tara.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.tara.designsystem.TaraCard
import dev.tara.designsystem.TaraPage
import dev.tara.domain.Capability

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    TaraPage("Meet Tara", "Your phone, with a little less friction.") {
        TaraCard {
            Text("Local by design", style = MaterialTheme.typography.titleLarge)
            Text("Type a command, or tap the microphone while Tara is open. Commands and audio stay on your device.")
        }
        TaraCard {
            Text("Real Android actions", style = MaterialTheme.typography.titleLarge)
            Text("Open apps, adjust media volume, use the flashlight, and lock your device with your authorization.")
            Text("Tara opens your dialer, messaging composer or clock. You finish calls, messages and clock setup in those apps.")
        }
        TaraCard {
            Text("Phase 1 · You stay in control", style = MaterialTheme.typography.titleLarge)
            Text("On-device speech depends on Android and installed language support. Without it, the microphone provides a capture diagnostic and typed commands remain available.")
            Text("No background listening, “Hey Tara”, or AI model is active in this phase.")
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("welcome_continue")) {
            Text("Get started")
        }
    }
}

@Composable
fun PermissionsOnboardingScreen(onContinue: () -> Unit, onPermission: (Capability) -> Unit) {
    TaraPage("Only the access you choose", "Every permission is optional. Typed commands work without microphone access.") {
        TaraCard {
            Text("Microphone", style = MaterialTheme.typography.titleLarge)
            Text("Used only after you tap the microphone. Stop or cancel at any time; leaving Tara releases capture.")
            OutlinedButton(onClick = { onPermission(Capability.MICROPHONE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set up microphone") }
        }
        TaraCard {
            Text("Device lock", style = MaterialTheme.typography.titleLarge)
            Text("Locking needs Android device-administrator approval and a secure screen lock. Tara requests only the force-lock policy. You can revoke access in Android settings.")
            OutlinedButton(onClick = { onPermission(Capability.DEVICE_ADMIN) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set up device lock") }
        }
        Text("Flashlight access is explained when needed. The system contact picker lets you select a number without giving Tara access to your address book.")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("permissions_continue")) {
            Text("Continue to assistant")
        }
    }
}
