package dev.voice.offline.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class AssistantCapabilityChecker(private val context: Context) {
    val microphoneGranted: Boolean get() = ContextCompat.checkSelfPermission(context,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val isActiveAssistant: Boolean get() = android.service.voice.VoiceInteractionService
        .isActiveService(context, ComponentName(context, VoiceInteractionService::class.java))

    // An assistant role or a device feature flag does not establish DSP/enrollment access.
    val hotwordDescription: String get() =
        "Hardware hotword detection is not enabled. Optional Hey Voice uses Vosk software " +
            "monitoring with an active microphone and Android privacy indicator. Power use is unmeasured."
}
