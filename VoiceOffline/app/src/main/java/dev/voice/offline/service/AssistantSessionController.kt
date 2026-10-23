package dev.voice.offline.service

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListeningMode { ON_DEMAND, HANDS_FREE }

/** All native recognizers are used and closed by the owning session coroutine. */
interface AssistantSessionResources : AutoCloseable {
    suspend fun prepare(mode: ListeningMode)
    suspend fun captureCommand(mode: ListeningMode, transition: (AssistantState) -> Unit): String
    fun stopCapture()
    suspend fun processTranscript(text: String, transition: (AssistantState) -> Unit)
}

class AssistantSessionController(
    private val resources: AssistantSessionResources,
    private val publish: (AssistantState) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<AssistantState>(AssistantState.IDLE)
    val state = mutableState.asStateFlow()

    private fun transition(state: AssistantState) {
        mutableState.value = state
        publish(state)
    }

    suspend fun run(mode: ListeningMode): Boolean {
        if (!running.compareAndSet(false, true)) return false
        try {
            transition(AssistantState.ACTIVATING)
            resources.prepare(mode)
            do {
                currentCoroutineContext().ensureActive()
                val transcript = try {
                    resources.captureCommand(mode, ::transition)
                } finally {
                    resources.stopCapture()
                }
                currentCoroutineContext().ensureActive()
                transition(AssistantState.RECOGNIZING)
                if (transcript.isNotBlank()) {
                    transition(AssistantState.INFERENCING)
                    resources.processTranscript(transcript, ::transition)
                }
            } while (mode == ListeningMode.HANDS_FREE)
            return true
        } finally {
            transition(AssistantState.CLEANUP)
            try {
                resources.stopCapture()
            } finally {
                try { resources.close() } finally {
                    transition(AssistantState.IDLE)
                    running.set(false)
                }
            }
        }
    }
}
