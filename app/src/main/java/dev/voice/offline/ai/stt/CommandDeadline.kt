package dev.voice.offline.ai.stt

/** Monotonic deadlines, independent of Vosk partial-result repetition. */
class CommandDeadline(maxDurationMs: Long, private val silenceMs: Long, startedAtMs: Long) {
    private val maximum = startedAtMs + maxDurationMs
    private var silence = startedAtMs + silenceMs
    private var lastPartial = ""

    fun onPartial(text: String, nowMs: Long) {
        if (text.isNotBlank() && text != lastPartial) {
            lastPartial = text
            silence = nowMs + silenceMs
        }
    }

    fun hasExpired(nowMs: Long): Boolean = nowMs >= maximum || nowMs >= silence
}
