package dev.voice.offline.audio

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** One microphone stream, two isolated decoding states; no network or Android SpeechRecognizer. */
class OfflineSpeechEngine(modelPath: String, private val onCommand: (String) -> Unit) : SpeechPipeline {
    private val model = Model(modelPath)
    private val wake = Recognizer(model, 16000f, "[\"hey tara\",\"okay tara\",\"tara\",\"hey voice\",\"okay voice\",\"[unk]\"]")
    private val command = Recognizer(model, 16000f)
    private var listeningForCommand = false
    private var active = false
    private var handsFree = false
    private var commandDeadlineNanos = 0L

    override fun startListening(handsFree: Boolean) {
        this.handsFree = handsFree
        active = true
        listeningForCommand = !handsFree
        commandDeadlineNanos = System.nanoTime() + 7_000_000_000L
        command.reset()
        wake.reset()
    }

    override fun stopListening() {
        active = false
        listeningForCommand = false
        command.reset()
        wake.reset()
    }

    override fun offer(buffer: ShortArray, count: Int) {
        if (!active) return
        if (listeningForCommand) {
            if (command.acceptWaveForm(buffer, count)) {
                val recognized = JSONObject(command.result).optString("text")
                if (recognized.isNotBlank()) {
                    finish(recognized)
                    return
                }
            }
            if (System.nanoTime() >= commandDeadlineNanos) {
                finish(JSONObject(command.finalResult).optString("text"))
            }
        } else {
            if (wake.acceptWaveForm(buffer, count)) {
                val recognized = JSONObject(wake.result).optString("text")
                if (recognized == "hey tara" || recognized == "okay tara" || recognized == "tara" || recognized == "hey voice" || recognized == "okay voice") {
                    listeningForCommand = true
                    commandDeadlineNanos = System.nanoTime() + 7_000_000_000L
                    command.reset()
                }
                wake.reset()
            }
        }
    }

    private fun finish(text: String) {
        listeningForCommand = false
        active = handsFree
        wake.reset()
        command.reset()
        if (text.isNotBlank()) onCommand(text)
    }

    override fun close() {
        command.close()
        wake.close()
        model.close()
    }
}
