package dev.voice.offline.core.audio

/** Swappable offline recognizer boundary for dedicated KWS + STT or acoustic classifier. */
interface SpeechPipeline : AutoCloseable {
    fun startListening(handsFree: Boolean = false)
    fun stopListening()
    fun offer(buffer: ShortArray, count: Int)
}
