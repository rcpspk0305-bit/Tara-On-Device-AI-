package dev.voice.offline.wakeword

import dev.voice.offline.ai.wakeword.WakeWordResult
import org.junit.Assert.*
import org.junit.Test

class OnnxWakeWordDetectorTest {

    private class MockRunner(private val scoreProvider: () -> Float) : OnnxInferenceRunner {
        var closed = false
        override fun predict(features: Array<FloatArray>): FloatArray {
            val score = scoreProvider()
            return floatArrayOf(1f - score, score)
        }
        override fun close() {
            closed = true
        }
    }

    @Test
    fun testSilenceDoesNotTriggerWakeWord() {
        val runner = MockRunner { 0.1f }
        val detector = OnnxWakeWordDetector(runner = runner, sensitivity = 0.65f, cooldownMs = 2000L)

        // 16,000 samples of silence (1 second at 16kHz)
        val silence = ShortArray(1600)
        var result: WakeWordResult = WakeWordResult.None
        for (i in 0 until 10) {
            val r = detector.detect(silence, silence.size)
            if (r !is WakeWordResult.None) result = r
        }

        assertEquals(WakeWordResult.None, result)
        detector.close()
        assertTrue(runner.closed)
    }

    @Test
    fun testWakeWordDetectedWhenScoreExceedsSensitivity() {
        var score = 0.1f
        val runner = MockRunner { score }
        val detector = OnnxWakeWordDetector(runner = runner, sensitivity = 0.65f, cooldownMs = 2000L)

        val audio = ShortArray(1600) { (it % 100).toShort() }
        // Feed initial 9 blocks
        for (i in 0 until 9) {
            val r = detector.detect(audio, audio.size)
            assertEquals(WakeWordResult.None, r)
        }

        // Now score rises above sensitivity
        score = 0.85f
        val detectedResult = detector.detect(audio, audio.size)
        assertTrue("Expected Detected result but got $detectedResult", detectedResult is WakeWordResult.Detected)
        assertEquals("hey tara", (detectedResult as WakeWordResult.Detected).phrase)

        detector.close()
    }

    @Test
    fun testCooldownSuppressesImmediateSubsequentDetections() {
        var score = 0.85f
        val runner = MockRunner { score }
        val detector = OnnxWakeWordDetector(runner = runner, sensitivity = 0.65f, cooldownMs = 2000L)

        val audio = ShortArray(1600) { (it % 100).toShort() }
        var detected = false
        for (i in 0 until 10) {
            val r = detector.detect(audio, audio.size)
            if (r is WakeWordResult.Detected) {
                detected = true
                break
            }
        }
        assertTrue("Initial detection must succeed", detected)

        // Subsequent audio chunk during cooldown window
        val cooldownResult = detector.detect(audio, audio.size)
        assertEquals(WakeWordResult.InCooldown, cooldownResult)

        detector.close()
    }

    @Test
    fun testSensitivityAdjustmentThreshold() {
        val runner = MockRunner { 0.70f }
        val detector = OnnxWakeWordDetector(runner = runner, sensitivity = 0.80f, cooldownMs = 2000L)

        val audio = ShortArray(1600) { (it % 100).toShort() }
        for (i in 0 until 10) {
            assertEquals(WakeWordResult.None, detector.detect(audio, audio.size))
        }

        // Lower sensitivity so 0.70 exceeds it
        detector.sensitivity = 0.60f
        detector.reset()

        var detected = false
        for (i in 0 until 10) {
            val r = detector.detect(audio, audio.size)
            if (r is WakeWordResult.Detected) {
                detected = true
                break
            }
        }
        assertTrue("Lowered sensitivity should trigger detection", detected)

        detector.close()
    }

    @Test
    fun testLongRunningBufferNoOverflow() {
        val runner = MockRunner { 0.1f }
        val detector = OnnxWakeWordDetector(runner = runner, sensitivity = 0.65f, cooldownMs = 2000L)

        val audio = ShortArray(1600)
        // Feed 100 blocks (160,000 samples) - well past 1.0s window
        for (i in 0 until 100) {
            val r = detector.detect(audio, audio.size)
            assertEquals(WakeWordResult.None, r)
        }
        detector.close()
    }

    @Test
    fun testDefaultRunnerAcousticEvaluation() {
        val runner = DefaultOnnxInferenceRunner()
        val dummyFeatures = Array(98) { FloatArray(39) { 0.05f } }
        val probs = runner.predict(dummyFeatures)
        assertEquals(2, probs.size)
        assertTrue("Probabilities should sum to approximately 1.0", (probs[0] + probs[1] - 1.0f) < 0.01f)
        assertTrue(probs[0] in 0f..1f)
        assertTrue(probs[1] in 0f..1f)
        runner.close()
    }
}

