package dev.voice.offline.core.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Nonblocking reads make cancellation bounded even when a device stops supplying audio. */
class AudioRecordManager(
    private val audioManager: AudioManager,
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (ShortArray, Int) -> Unit
) : AutoCloseable {
    private companion object {
        val processCapture = AtomicBoolean(false)
    }
    private val lock = Any()
    private var record: AudioRecord? = null
    private val capturing = AtomicBoolean(false)
    private val interruption = AtomicReference<String?>(null)

    suspend fun capture(onStarted: () -> Unit) {
        check(processCapture.compareAndSet(false, true)) { "Another Voice capture session is active" }
        check(capturing.compareAndSet(false, true)) { "Microphone capture already active" }
        val context = currentCoroutineContext()
        try {
            synchronized(lock) {
                context.ensureActive()
                checkCallInterruption()
                val minimum = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "16 kHz microphone capture is unsupported" }
                val active = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
                record = active
                check(active.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                active.registerAudioRecordingCallback(java.util.concurrent.Executor { it.run() },
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                            if (configs.any { it.isClientSilenced }) {
                                interruption.set("Microphone capture was silenced by Android or another application")
                            }
                        }
                    })
                active.startRecording()
                check(active.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
                check(active.activeRecordingConfiguration?.isClientSilenced != true) { "Microphone capture is silenced" }
                android.util.Log.i("VoiceLifecycle", "AudioRecord started")
                context.ensureActive()
                onStarted()
            }
            val buffer = ShortArray(1600)
            while (capturing.get()) {
                context.ensureActive()
                interruption.get()?.let { error(it) }
                checkCallInterruption()
                val count = synchronized(lock) {
                    record?.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                }
                check(count >= 0) { "Microphone read failed ($count)" }
                if (count > 0) onAudioChunk(buffer, count) else delay(10)
            }
        } catch (denied: SecurityException) {
            throw IllegalStateException("Microphone permission was denied or revoked", denied)
        } finally {
            try { stop() } finally { processCapture.set(false) }
        }
    }

    fun stop() {
        synchronized(lock) {
            val active = record
            record = null
            try {
                runCatching { if (active?.recordingState == AudioRecord.RECORDSTATE_RECORDING) active.stop() }
            } finally {
                try {
                    active?.release()
                    if (active != null) android.util.Log.i("VoiceLifecycle", "AudioRecord released")
                } finally { capturing.set(false) }
            }
        }
    }

    private fun checkCallInterruption() {
        check(audioManager.mode != AudioManager.MODE_IN_CALL &&
            audioManager.mode != AudioManager.MODE_IN_COMMUNICATION &&
            audioManager.mode != AudioManager.MODE_RINGTONE) {
            "Microphone interaction interrupted by a call"
        }
    }

    override fun close() = stop()
}
