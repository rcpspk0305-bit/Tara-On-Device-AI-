package dev.voice.offline.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.voice.offline.service.VoiceAssistantService
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.MainActivity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Compact system-provided invocation surface; no screen data or lock-screen bypass. */
class VoiceInteractionSession(private val appContext: Context) :
    android.service.voice.VoiceInteractionSession(appContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observation: Job? = null
    private var invocation: String? = null
    private var status: TextView? = null

    override fun onCreateContentView(): View = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 24, 32, 24)
        status = TextView(appContext).apply {
            text = "Voice is preparing"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(status)
        addView(Button(appContext).apply {
            text = "Cancel"
            setOnClickListener { cancelInvocation(); finish() }
        })
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (invocation != null) return
        if (!AssistantCapabilityChecker(appContext).microphoneGranted) {
            // A visible activity owns runtime permission requests; a session does not grant them.
            try { startAssistantActivity(Intent(appContext, MainActivity::class.java)) }
            catch (_: Exception) { }
            finish()
            return
        }
        val id = UUID.randomUUID().toString()
        invocation = id
        observation = scope.launch {
            VoiceAssistantService.stateFlow.collect { state ->
                status?.text = when (state) {
                    AssistantState.LISTENING -> "Microphone active. Say one command."
                    AssistantState.ACTIVATING -> "Preparing offline speech. Microphone off."
                    AssistantState.AUTHORIZING -> "Approval is waiting in your notifications. Microphone off."
                    AssistantState.IDLE -> "Microphone off."
                    else -> "Processing command. Microphone off."
                }
            }
        }
        if (!AssistantInvocationRouter.start(appContext, id) { message ->
            if (invocation == id) {
                observation?.cancel()
                status?.text = message
                scope.launch {
                    delay(900)
                    if (invocation == id) finish()
                }
            }
        }) {
            status?.text = "Open Voice to enable microphone access and try again."
            finish()
        }
    }

    private fun cancelInvocation() {
        observation?.cancel()
        observation = null
        invocation?.let { AssistantInvocationRouter.cancel(appContext, it) }
        invocation = null
    }

    override fun onHide() {
        cancelInvocation()
        super.onHide()
    }

    override fun onDestroy() {
        cancelInvocation()
        scope.cancel()
        super.onDestroy()
    }
}
