package dev.voice.offline.ai.stt

import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class VoskSpeechRecognizer(
    model: Model
) : OfflineSpeechRecognizer {

    private val recognizer = Recognizer(model, 16000f)
    private var deadline: CommandDeadline? = null

    override fun startListening(maxDurationMs: Long, silenceTimeoutMs: Long) {
        recognizer.reset()
        val now = currentTime()
        deadline = CommandDeadline(maxDurationMs, silenceTimeoutMs, now)
    }

    override fun offer(buffer: ShortArray, count: Int): SpeechRecognitionResult {
        val now = currentTime()
        if (deadline?.hasExpired(now) != false) {
            val text = finish()
            return if (text.isNotBlank()) SpeechRecognitionResult.Final(text)
            else SpeechRecognitionResult.SilenceTimeout
        }

        if (recognizer.acceptWaveForm(buffer, count)) {
            val text = parseResultText(recognizer.result)
            if (text.isNotBlank()) {
                return SpeechRecognitionResult.Final(text)
            }
        } else {
            val partial = parsePartialText(recognizer.partialResult)
            if (partial.isNotBlank()) {
                deadline?.onPartial(partial, now)
                return SpeechRecognitionResult.Partial(partial)
            }
        }

        return SpeechRecognitionResult.Listening
    }

    override fun finish(): String {
        val text = parseResultText(recognizer.finalResult)
        reset()
        return text
    }

    override fun reset() {
        recognizer.reset()
        deadline = null
    }

    override fun close() {
        recognizer.close()
    }

    private fun parseResultText(json: String): String {
        return try {
            JSONObject(json).optString("text").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parsePartialText(json: String): String {
        return try {
            JSONObject(json).optString("partial").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun currentTime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}
