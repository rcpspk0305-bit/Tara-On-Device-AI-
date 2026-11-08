package dev.voice.offline.ai.wakeword

import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class VoskWakeWordDetector(
    model: Model,
    override var sensitivity: Float = 0.5f,
    override var cooldownMs: Long = 2000L
) : WakeWordDetector {

    private val recognizer = Recognizer(model, 16000f, "[\"hey tara\",\"okay tara\",\"tara\",\"hey voice\",\"okay voice\",\"[unk]\"]")
    private var cooldownDeadline = 0L

    override fun detect(buffer: ShortArray, count: Int): WakeWordResult {
        val now = currentTime()
        if (now < cooldownDeadline) {
            return WakeWordResult.InCooldown
        }

        if (recognizer.acceptWaveForm(buffer, count)) {
            val text = parseResultText(recognizer.result)
            recognizer.reset()
            if (text == "hey tara" || text == "okay tara" || text == "tara" || text == "hey voice" || text == "okay voice") {
                cooldownDeadline = now + cooldownMs
                return WakeWordResult.Detected(text)
            }
        }
        return WakeWordResult.None
    }

    override fun reset() {
        recognizer.reset()
        cooldownDeadline = 0L
    }

    override fun close() {
        recognizer.close()
    }

    private fun parseResultText(json: String): String {
        return try {
            JSONObject(json).optString("text").trim().lowercase()
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
