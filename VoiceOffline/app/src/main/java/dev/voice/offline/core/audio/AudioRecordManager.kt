package dev.voice.offline.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the native Android AudioRecord microphone capture loop in a dedicated coroutine.
 * Guarantees that audio capture and processing never block the UI thread.
 */
class AudioRecordManager(
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (ShortArray, Int) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {

    private val isRecording = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val recordLock = Any()

    fun start(scope: CoroutineScope) {
        if (!isRecording.compareAndSet(false, true)) return

        recordJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                onError("16 kHz Mono PCM audio recording is not supported on this device")
                isRecording.set(false)
                return@launch
            }

            val bufferSize = maxOf(minBuf, 6400)
            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                synchronized(recordLock) { audioRecord = record }
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Microphone is unavailable or recording permission was denied")
                    isRecording.set(false)
                    return@launch
                }

                record.startRecording()

                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    onError("Microphone is currently held by another application")
                    isRecording.set(false)
                    return@launch
                }

                val buffer = ShortArray(1600) // 100ms chunks at 16kHz
                while (isActive && isRecording.get()) {
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        onAudioChunk(buffer, read)
                    } else if (read < 0) {
                        onError("Microphone read error: code $read")
                        break
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: SecurityException) {
                onError("Microphone permission was denied or revoked")
            } catch (e: Exception) {
                onError("Audio capture interrupted: ${e.message}")
            } finally {
                stopInternal()
            }
        }
    }

    fun stop() {
        isRecording.set(false)
        recordJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        val record = synchronized(recordLock) {
            audioRecord.also { audioRecord = null }
        }
        try {
            try { record?.stop() } catch (_: Exception) {}
            record?.release()
        } catch (_: Exception) {
        } finally {
            isRecording.set(false)
        }
    }

    override fun close() {
        stop()
    }
}
