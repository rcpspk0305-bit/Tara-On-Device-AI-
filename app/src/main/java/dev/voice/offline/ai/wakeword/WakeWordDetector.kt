package dev.voice.offline.ai.wakeword

sealed interface WakeWordResult {
    data class Detected(val phrase: String) : WakeWordResult
    data object InCooldown : WakeWordResult
    data object None : WakeWordResult
}

/**
 * Abstraction for on-device wake-word detection.
 * Supports configurable sensitivity, cooldown to reduce duplicate activations,
 * and false positive mitigation.
 */
interface WakeWordDetector : AutoCloseable {
    var sensitivity: Float
    var cooldownMs: Long
    fun detect(buffer: ShortArray, count: Int): WakeWordResult
    fun reset()
}
