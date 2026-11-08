package dev.voice.offline.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import dev.voice.offline.ai.functioncalling.StructuredAction

enum class BiometricReadiness {
    READY,
    NOT_ENROLLED,
    HARDWARE_UNAVAILABLE,
    UNSUPPORTED
}

/**
 * Manages device biometric capabilities, StrongBox detection,
 * and BiometricPrompt configuration for high-risk Voice actions.
 */
class BiometricAuthorizationManager(private val context: Context) {

    fun checkBiometricReadiness(): BiometricReadiness {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricReadiness.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricReadiness.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricReadiness.HARDWARE_UNAVAILABLE
            else -> BiometricReadiness.UNSUPPORTED
        }
    }

    fun buildPromptInfo(action: StructuredAction): BiometricPrompt.PromptInfo {
        val title = "Authorize ${action.functionName.replace('_', ' ').capitalizeFirst()}"
        val subtitle = when (action) {
            is StructuredAction.MakeCall -> "Call ${action.contactName}"
            is StructuredAction.TextMsg -> "Send text to ${action.contactName}"
            is StructuredAction.ReplyToNotification -> "Reply to notification"
            else -> "Approve action execution"
        }

        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription("Biometric authentication required before executing sensitive command")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun String.capitalizeFirst(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
