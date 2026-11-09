package dev.tara.audio

import kotlinx.coroutines.flow.StateFlow

sealed interface AudioState {
    data object Idle : AudioState
    data object Listening : AudioState
    data object Transcribing : AudioState
    data class Transcript(val text: String) : AudioState
    data class Diagnostic(val message: String) : AudioState
    data class Error(val message: String) : AudioState
}

/** All calls occur on the main thread; cancel on the host Activity's onStop. */
interface SpeechController : AutoCloseable {
    val state: StateFlow<AudioState>
    fun start()
    fun stop()
    fun cancel()
    override fun close()
}

/** Invalidates late callbacks and guarantees that a resource is closed at most once. */
internal class CaptureSession {
    private var generation = 0L
    private var release: (() -> Unit)? = null
    fun begin(cleanup: () -> Unit): Long {
        end()
        release = cleanup
        return generation
    }
    fun current(token: Long): Boolean = release != null && token == generation
    fun end() {
        generation++
        val cleanup = release
        release = null
        cleanup?.invoke()
    }
}
