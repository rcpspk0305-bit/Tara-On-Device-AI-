package dev.voice.offline.ai.stt

sealed interface SpeechRecognitionResult {
    data class Partial(val text: String) : SpeechRecognitionResult
    data class Final(val text: String) : SpeechRecognitionResult
    data object SilenceTimeout : SpeechRecognitionResult
    data object MaxDurationExceeded : SpeechRecognitionResult
    data object Listening : SpeechRecognitionResult
}

/**
 * Offline Speech-To-Text interface with Voice Activity Detection (VAD),
 * endpoint detection, and silence timeout handling.
 */
interface OfflineSpeechRecognizer : AutoCloseable {
    fun startListening(maxDurationMs: Long = 7000L, silenceTimeoutMs: Long = 3000L)
    fun offer(buffer: ShortArray, count: Int): SpeechRecognitionResult
    fun finish(): String
    fun reset()
}
