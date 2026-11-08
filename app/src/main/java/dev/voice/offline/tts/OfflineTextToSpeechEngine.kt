package dev.voice.offline.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface abstracting the underlying TTS platform driver.
 */
interface TtsSpeaker {
    fun isAvailable(): Boolean
    fun speak(text: String, flush: Boolean, onComplete: (() -> Unit)? = null): Boolean
    fun stop()
    fun shutdown()
}

/**
 * Android framework implementation of [TtsSpeaker] using [android.speech.tts.TextToSpeech].
 * Configures offline embedded synthesis to guarantee privacy and zero network queries.
 */
class AndroidTtsSpeaker(context: Context) : TtsSpeaker {
    private val ready = AtomicBoolean(false)
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ready.set(true)
        }
    }

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    callbacks.remove(utteranceId)?.invoke()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                    callbacks.remove(utteranceId)?.invoke()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null) {
                    callbacks.remove(utteranceId)?.invoke()
                }
            }
        })
    }

    override fun isAvailable(): Boolean = ready.get()

    override fun speak(text: String, flush: Boolean, onComplete: (() -> Unit)?): Boolean {
        val engine = tts ?: return false
        if (!ready.get()) return false

        val utteranceId = UUID.randomUUID().toString()
        if (onComplete != null) {
            callbacks[utteranceId] = onComplete
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true")
        }

        val result = engine.speak(text, queueMode, params, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    override fun stop() {
        callbacks.clear()
        tts?.stop()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}

/**
 * On-device offline Text-To-Speech engine providing spoken feedback
 * for assistant actions and error explanations without sending data off-device.
 */
class OfflineTextToSpeechEngine(
    private val speaker: TtsSpeaker
) : AutoCloseable {

    constructor(context: Context) : this(AndroidTtsSpeaker(context))

    val isReady: Boolean get() = speaker.isAvailable()

    fun speak(text: String, flush: Boolean = true, onComplete: (() -> Unit)? = null): Boolean {
        if (text.isBlank()) return false
        return speaker.speak(text.trim(), flush, onComplete)
    }

    fun confirmSuccess(message: String, onComplete: (() -> Unit)? = null): Boolean {
        return speak(message, flush = true, onComplete = onComplete)
    }

    fun explainFailure(reason: String, onComplete: (() -> Unit)? = null): Boolean {
        return speak(reason, flush = true, onComplete = onComplete)
    }

    fun stop() {
        speaker.stop()
    }

    override fun close() {
        speaker.shutdown()
    }
}

