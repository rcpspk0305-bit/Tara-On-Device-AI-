package dev.tara.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tara.designsystem.TaraCard
import dev.tara.designsystem.TaraPage
import dev.tara.settingsdata.TaraSettings
import dev.tara.settingsdata.ThemeMode

@Composable
fun SettingsScreen(settings: TaraSettings, onTheme: (ThemeMode) -> Unit, onResetOnboarding: () -> Unit) {
    TaraPage("Settings", "Make Tara comfortable for you.") {
        TaraCard {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    OutlinedButton(onClick = { onTheme(mode) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("${mode.name.lowercase().replaceFirstChar(Char::uppercase)}${if (settings.theme == mode) " · selected" else ""}")
                    }
                }
            }
        }
        TaraCard {
            Text("Learn the controls", style = MaterialTheme.typography.titleLarge)
            Text("Review what Phase 1 can do and how permission setup works.")
            OutlinedButton(onClick = onResetOnboarding, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review onboarding") }
        }
        TaraCard {
            Text("Phase 1", style = MaterialTheme.typography.titleLarge)
            Text("Deterministic local commands. Push-to-talk when native on-device recognition is available, with a microphone diagnostic otherwise.")
            Text("Offline response speech, wake-word listening and FunctionGemma are future milestones.")
        }
    }
}
