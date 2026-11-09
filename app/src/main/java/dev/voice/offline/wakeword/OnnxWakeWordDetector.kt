package dev.voice.offline.wakeword

import android.os.SystemClock
import dev.voice.offline.ai.wakeword.WakeWordDetector
import dev.voice.offline.ai.wakeword.WakeWordResult
import dev.voice.offline.core.audio.Mfcc39Extractor

/**
 * On-device neural wake-word detector for "Hey Tara".
 * Runs lightweight local inference using an Android-compatible ONNX model graph.
 *
 * Audio is processed via a 16kHz circular sliding frame buffer. Acoustic features
 * (39-dim MFCCs) are extracted every 100ms hop and evaluated against the trained
 * classifier. Full speech recognition remains idle until a verified detection.
 */
class OnnxWakeWordDetector(
    private val runner: OnnxInferenceRunner = DefaultOnnxInferenceRunner(),
    override var sensitivity: Float = 0.65f,
    override var cooldownMs: Long = 2000L
) : WakeWordDetector {

    companion object {
        const val WAKE_PHRASE = "hey tara"
        private const val WINDOW_SIZE = 16_000 // 1.0 second at 16kHz
        private const val HOP_SIZE = 1_600     // 100ms evaluation interval
    }

    private val mfccExtractor = Mfcc39Extractor()
    private val ringBuffer = ShortArray(WINDOW_SIZE)
    private var writePos = 0
    private var totalSamplesBuffered = 0
    private var samplesSinceLastInference = 0
    private var cooldownDeadline = 0L

    override fun detect(buffer: ShortArray, count: Int): WakeWordResult {
        val now = currentTime()
        if (now < cooldownDeadline) {
            return WakeWordResult.InCooldown
        }

        val safeCount = minOf(count, buffer.size)

        // Buffer audio chunks
        for (i in 0 until safeCount) {
            ringBuffer[writePos] = buffer[i]
            writePos = (writePos + 1) % WINDOW_SIZE
        }
        totalSamplesBuffered = minOf(WINDOW_SIZE, totalSamplesBuffered + safeCount)
        samplesSinceLastInference += safeCount

        // Wait until we have accumulated at least 1.0s and reached a 100ms hop
        if (totalSamplesBuffered < WINDOW_SIZE || samplesSinceLastInference < HOP_SIZE) {
            return WakeWordResult.None
        }
        samplesSinceLastInference = 0

        // Linearize circular buffer for feature extraction
        val linearPcm = ShortArray(WINDOW_SIZE)
        val firstPartLen = WINDOW_SIZE - writePos
        System.arraycopy(ringBuffer, writePos, linearPcm, 0, firstPartLen)
        System.arraycopy(ringBuffer, 0, linearPcm, firstPartLen, writePos)

        // Extract 39-dim MFCC features (produces 98 frames)
        val frames = mfccExtractor.extract(linearPcm)
        if (frames.size < 98) {
            return WakeWordResult.None
        }

        // Run lightweight ONNX inference
        val features = frames.take(98).toTypedArray()
        val probs = runner.predict(features)
        val heyTaraScore = probs.getOrNull(1) ?: 0f

        if (heyTaraScore >= sensitivity) {
            cooldownDeadline = now + cooldownMs
            resetBuffer()
            return WakeWordResult.Detected(WAKE_PHRASE)
        }

        return WakeWordResult.None
    }

    override fun reset() {
        resetBuffer()
        cooldownDeadline = 0L
    }

    private fun resetBuffer() {
        ringBuffer.fill(0)
        writePos = 0
        totalSamplesBuffered = 0
        samplesSinceLastInference = 0
    }

    override fun close() {
        runner.close()
    }

    private fun currentTime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}

