package dev.voice.offline.ui.security

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ActionExecutorRegistry
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.core.security.BiometricAuthorizationManager
import dev.voice.offline.core.security.BiometricReadiness
import dev.voice.offline.core.security.KeystoreManager
import dev.voice.offline.core.security.PendingCommandStore
import dev.voice.offline.core.security.SensitiveActionPolicy
import dev.voice.offline.service.VoiceAssistantService

/**
 * Foreground-only, user-tapped entry point for approving sensitive Voice actions.
 * NEVER started directly from a background service (respects Android 14+ background launch limits).
 */
class AuthorizationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val token = intent.getStringExtra("token")
        val action = token?.let(PendingCommandStore.default::peek)
        if (action == null) {
            finish()
            return
        }

        val text = TextView(this).apply {
            this.text = "Voice Action Approval\n\n${describeAction(action)}"
            textSize = 20f
            setPadding(48, 90, 48, 60)
        }
        if (!SensitiveActionPolicy().requiresBiometrics(action)) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(text)
                addView(Button(this@AuthorizationActivity).apply {
                    this.text = "Confirm action"
                    setOnClickListener {
                        isEnabled = false
                        executeApproved(token!!, text)
                    }
                })
                addView(Button(this@AuthorizationActivity).apply {
                    this.text = "Cancel"
                    setOnClickListener {
                        PendingCommandStore.default.take(token!!)
                        finish()
                    }
                })
            }
            setContentView(layout)
            return
        }
        setContentView(text)

        val bioManager = BiometricAuthorizationManager(this)
        val readiness = bioManager.checkBiometricReadiness()

        if (readiness != BiometricReadiness.READY) {
            text.text = "Biometric authentication unavailable ($readiness).\nPlease configure biometric security in Android settings."
            return
        }

        try {
            val signature = KeystoreManager.freshBiometricSignature()
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val ok = try {
                            val authenticated = result.cryptoObject?.signature ?: run { finish(); return }
                            authenticated.update(token!!.toByteArray(Charsets.UTF_8))
                            authenticated.sign().isNotEmpty()
                        } catch (_: Exception) { false }

                        if (!ok) {
                            finish()
                            return
                        }

                        executeApproved(token!!, text)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        PendingCommandStore.default.take(token!!)
                        text.text = "Authentication cancelled: $errString"
                        text.postDelayed({ finish() }, 1000)
                    }
                }
            )

            val info = bioManager.buildPromptInfo(action)
            prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
        } catch (e: Exception) {
            text.text = "Secure biometric hardware authentication unavailable: ${e.message}"
        }
    }

    private fun executeApproved(token: String, text: TextView) {
        val approved = PendingCommandStore.default.take(token) ?: run {
            text.text = "Action expired or already consumed"
            return
        }
        val outcome = ActionExecutorRegistry(this).execute(approved)
        text.text = outcome.message
        VoiceAssistantService.reportActionResult(outcome.message)
        if (outcome.isAccepted) {
            val chime = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30) }.getOrNull()
            runCatching { chime?.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
            text.postDelayed({ chime?.release() }, 170)
        }
        text.postDelayed({ finish() }, 1200)
    }

    private fun describeAction(action: StructuredAction): String = when (action) {
        is StructuredAction.MakeCall -> "Make phone call to: ${action.contactName}"
        is StructuredAction.TextMsg -> "Send message to ${action.contactName}:\n\"${action.body}\""
        is StructuredAction.ReplyToNotification -> "Reply to notification (${action.notificationId}):\n\"${action.body}\""
        is StructuredAction.SetAlarm -> "Set alarm for ${String.format("%02d:%02d", action.hour, action.minute)}"
        is StructuredAction.TakeNotes -> "Save note:\n\"${action.content}\""
        StructuredAction.OpenCamera -> "Open device camera"
        StructuredAction.LockScreen -> "Lock screen"
        StructuredAction.TurnFlashlightOn -> "Turn flashlight ON"
        StructuredAction.TurnFlashlightOff -> "Turn flashlight OFF"
        StructuredAction.AnswerCall -> "Answer incoming call"
        StructuredAction.RejectCall -> "Reject incoming call"
        StructuredAction.ReadNotifications -> "Read recent notifications"
        is StructuredAction.OpenApp -> "Open app: ${action.packageName}"
    }
}
