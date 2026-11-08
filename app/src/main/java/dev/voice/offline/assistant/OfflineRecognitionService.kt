package dev.voice.offline.assistant

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import dev.voice.offline.ai.stt.SpeechRecognitionResult
import dev.voice.offline.ai.stt.VoskSpeechRecognizer
import dev.voice.offline.ai.stt.AssetModelInstaller
import dev.voice.offline.core.audio.AudioRecordManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.vosk.Model

/** Required assistant metadata target; also serves real offline speech-protocol clients.
 * This surface transcribes only. It never interprets text as permission to execute an action.
 */
class OfflineRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var audio: AudioRecordManager? = null
    private val finishRequested = AtomicBoolean(false)

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        if (job?.isCompleted == false) {
            listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return
        }
        finishRequested.set(false)
        job = scope.launch {
            try {
                val path = AssetModelInstaller.install(applicationContext)
                val text = Model(path.absolutePath).use { model ->
                    VoskSpeechRecognizer(model).use { recognizer ->
                        capture(recognizer, listener)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (text.isBlank()) listener.error(SpeechRecognizer.ERROR_NO_MATCH)
                    else {
                        listener.endOfSpeech()
                        listener.results(Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                        })
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    runCatching { listener.error(if (error is SecurityException || error.cause is SecurityException)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS else SpeechRecognizer.ERROR_AUDIO) }
                }
            } finally {
                audio?.stop()
                audio = null
            }
        }
    }

    private suspend fun capture(recognizer: VoskSpeechRecognizer, listener: Callback): String = coroutineScope {
        val chunks = Channel<ShortArray>(4, BufferOverflow.DROP_OLDEST)
        val ready = CompletableDeferred<Unit>()
        val manager = AudioRecordManager(getSystemService(AudioManager::class.java), onAudioChunk = { data, count ->
            chunks.trySend(data.copyOf(count))
        })
        audio = manager
        val recording = launch {
            manager.capture {
                recognizer.startListening()
                listener.readyForSpeech(Bundle())
                ready.complete(Unit)
            }
        }
        try {
            ready.await()
            while (!finishRequested.get()) {
                val chunk = withTimeoutOrNull(1500) { chunks.receive() }
                    ?: error("Microphone stopped supplying audio")
                when (val result = recognizer.offer(chunk, chunk.size)) {
                    is SpeechRecognitionResult.Final -> return@coroutineScope result.text
                    SpeechRecognitionResult.SilenceTimeout, SpeechRecognitionResult.MaxDurationExceeded ->
                        return@coroutineScope recognizer.finish()
                    else -> Unit
                }
            }
            recognizer.finish()
        } finally {
            manager.stop()
            withContext(NonCancellable) { recording.cancelAndJoin() }
            chunks.close()
        }
    }

    override fun onStopListening(listener: Callback) { finishRequested.set(true) }

    override fun onCancel(listener: Callback) {
        job?.cancel()
        audio?.stop()
    }

    override fun onDestroy() {
        job?.cancel()
        audio?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
