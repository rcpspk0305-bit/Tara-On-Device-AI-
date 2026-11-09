package dev.tara.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** No network recognizer, background service, audio files, or simultaneous capture engines. */
class AndroidSpeechController(context: Context) : SpeechController {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow<AudioState>(AudioState.Idle)
    override val state: StateFlow<AudioState> = mutable.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = this.context.getSystemService(AudioManager::class.java)
    private val capture = CaptureSession()
    private var recognizer: SpeechRecognizer? = null
    private var timeout: Job? = null
    private var diagnosticJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private var focusGeneration = 0L
    private var closed = false
    @Volatile private var stopDiagnostic = false

    override fun start() {
        requireMain()
        if (closed) return
        if (mutable.value == AudioState.Listening || mutable.value == AudioState.Transcribing) return
        if (diagnosticJob?.isCompleted == false) {
            mutable.value = AudioState.Error("Microphone is still closing. Try again in a moment.")
            return
        }
        cancel()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutable.value = AudioState.Error("Microphone permission is required. Allow access, then tap Start.")
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            mutable.value = AudioState.Error("This device has no microphone.")
            return
        }
        if (!acquireFocus()) {
            mutable.value = AudioState.Error("Audio is in use by another interaction. Try again when it finishes.")
            return
        }
        mutable.value = AudioState.Listening
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                startRecognition()
            } else {
                startDiagnostic()
            }
        } catch (_: SecurityException) {
            fail("Microphone access was denied or revoked. Check Android permissions.")
        } catch (_: RuntimeException) {
            fail("On-device audio could not start. Use typed commands, or retry after checking microphone availability.")
        }
    }

    override fun stop() {
        requireMain()
        if (mutable.value != AudioState.Listening) return
        mutable.value = AudioState.Transcribing
        timeout?.cancel()
        if (recognizer != null) {
            try {
                recognizer?.stopListening()
                finishingTimeout()
            } catch (_: RuntimeException) {
                fail("Local recognizer stopped unexpectedly. Please retry.")
            }
        } else {
            stopDiagnostic = true
        }
    }

    override fun cancel() {
        requireMain()
        timeout?.cancel()
        timeout = null
        try { capture.end() } catch (_: RuntimeException) {
            // Continue abandoning focus even if a device-specific speech service fails to close.
        }
        diagnosticJob?.cancel()
        recognizer = null
        abandonFocus()
        mutable.value = AudioState.Idle
    }

    override fun close() {
        requireMain()
        if (closed) return
        cancel()
        closed = true
        scope.cancel()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun startRecognition() {
        val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = engine
        val token = capture.begin {
            try { engine.cancel() } catch (_: RuntimeException) {
                // Service death must not prevent destroy or microphone ownership cleanup.
            } finally {
                try { engine.destroy() } catch (_: RuntimeException) {
                    // A dead recognition service cannot retain a usable client object.
                }
            }
        }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onEndOfSpeech() {
                if (!capture.current(token) || mutable.value == AudioState.Transcribing) return
                mutable.value = AudioState.Transcribing
                finishingTimeout()
            }
            override fun onError(error: Int) {
                if (!capture.current(token)) return
                fail(when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access was denied or revoked. Check Android settings."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone capture was interrupted or is in use. Please retry."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was recognized locally. Try again or type a command."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The local speech model for this language is unavailable. Type a command or install it through Android settings."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The local speech recognizer is busy. Please retry."
                    else -> "Local speech recognition failed. Type a command or retry. No network fallback was used."
                })
            }
            override fun onResults(results: Bundle?) {
                if (!capture.current(token)) return
                val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                finishCapture()
                mutable.value = if (transcript.isEmpty()) AudioState.Error("No speech was recognized locally. Try again or type a command.")
                    else AudioState.Transcript(transcript)
            }
        })
        engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
        timeout = scope.launch {
            delay(15_000)
            if (capture.current(token) && mutable.value == AudioState.Listening) stop()
        }
    }

    private fun finishingTimeout() {
        timeout?.cancel()
        timeout = scope.launch {
            delay(5_000)
            fail("Local recognition timed out. The microphone has been released. Try again or type a command.")
        }
    }

    private fun startDiagnostic() {
        stopDiagnostic = false
        // The AudioRecord resource is owned and released on this coroutine's IO thread.
        val token = capture.begin { diagnosticJob?.cancel() }
        diagnosticJob = scope.launch {
            var frames = 0L
            var peak = 0
            var problem: String? = null
            try {
                withContext(Dispatchers.IO) {
                    val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    if (minimum <= 0) throw IllegalStateException("Unsupported capture format")
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("Microphone permission was revoked before capture started")
                    }
                    val recorder = AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum * 2, SAMPLE_RATE))
                        .build()
                    val sessionId = recorder.audioSessionId
                    val callback = object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                            if (capture.current(token) && configs.any { it.clientAudioSessionId == sessionId && it.isClientSilenced }) {
                                fail("Android silenced microphone capture. Check the microphone privacy switch and other recording apps.")
                            }
                        }
                    }
                    try {
                        if (recorder.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Capture initialization failed")
                        recorder.registerAudioRecordingCallback(context.mainExecutor, callback)
                        recorder.startRecording()
                        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("Capture did not start")
                        val samples = ShortArray(1600)
                        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
                        while (isActive && !stopDiagnostic && android.os.SystemClock.elapsedRealtime() < deadline) {
                            val read = recorder.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
                            if (read < 0) throw IllegalStateException("Capture read failed")
                            frames += read
                            for (index in 0 until read) peak = maxOf(peak, abs(samples[index].toInt()))
                            samples.fill(0)
                            delay(20)
                        }
                    } finally {
                        try { recorder.unregisterAudioRecordingCallback(callback) }
                        finally {
                            try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                            finally { recorder.release() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                problem = "Microphone access was revoked. No audio was saved. Check Android permissions."
            } catch (_: RuntimeException) {
                problem = "Microphone diagnostic failed. The capture resource was released. Check microphone availability."
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (capture.current(token)) {
                        capture.end()
                        abandonFocus()
                        mutable.value = if (problem != null) AudioState.Error(requireNotNull(problem))
                        else AudioState.Diagnostic("Native on-device recognition is unavailable. Captured ${frames * 1000 / SAMPLE_RATE} ms of microphone audio (peak $peak/32768); discarded all samples. Type a command to execute an action.")
                    }
                }
            }
        }
    }

    private fun acquireFocus(): Boolean {
        val manager = audioManager ?: return false
        val token = ++focusGeneration
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (token == focusGeneration && focus != null && (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
                    fail("Listening stopped because another audio interaction interrupted it.")
            }, handler)
            .build()
        focus = request
        return try {
            if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) true
            else { abandonFocus(); false }
        } catch (_: RuntimeException) { abandonFocus(); false }
    }

    private fun abandonFocus() {
        val request = focus
        focus = null
        focusGeneration++
        if (request != null) audioManager?.abandonAudioFocusRequest(request)
    }

    private fun finishCapture() {
        timeout?.cancel()
        timeout = null
        capture.end()
        recognizer = null
        abandonFocus()
    }

    private fun fail(message: String) {
        if (closed) return
        cancel()
        mutable.value = AudioState.Error(message)
    }

    private fun requireMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "SpeechController must be called on the main thread" } }

    private companion object { const val SAMPLE_RATE = 16_000 }
}
