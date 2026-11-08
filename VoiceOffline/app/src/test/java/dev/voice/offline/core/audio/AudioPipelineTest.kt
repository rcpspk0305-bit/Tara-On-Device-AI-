package dev.voice.offline.core.audio

import dev.voice.offline.ai.wakeword.WakeWordResult
import org.junit.Assert.*
import org.junit.Test

class AudioPipelineTest {

    @Test
    fun testAudioRingBufferWriteAndRead() {
        val ring = AudioRingBuffer(capacitySamples = 3200)
        assertEquals(0, ring.available)

        val input = ShortArray(1600) { it.toShort() }
        val written = ring.write(input, 1600)
        assertEquals(1600, written)
        assertEquals(1600, ring.available)

        val output = ShortArray(1600)
        val read = ring.read(output, 1600)
        assertEquals(1600, read)
        assertEquals(0, ring.available)
        assertEquals(input[0], output[0])
        assertEquals(input[1599], output[1599])
    }

    @Test
    fun testAudioRingBufferOverflowHandling() {
        val ring = AudioRingBuffer(capacitySamples = 1000)
        val chunk = ShortArray(600) { 1 }

        ring.write(chunk, 600)
        assertEquals(600, ring.available)

        // Writing another 600 will exceed 1000 capacity; bounded drop behavior
        val written = ring.write(chunk, 600)
        assertEquals(400, written)
        assertEquals(1000, ring.available)
    }

    @Test
    fun testWakeWordCooldownMechanism() {
        var cooldownNanos = 0L
        val cooldownDurationNanos = 2_000_000_000L // 2 seconds

        fun checkWakeWord(isDetected: Boolean, phrase: String, currentNanos: Long): WakeWordResult {
            if (currentNanos < cooldownNanos) {
                return WakeWordResult.InCooldown
            }
            if (isDetected) {
                cooldownNanos = currentNanos + cooldownDurationNanos
                return WakeWordResult.Detected(phrase)
            }
            return WakeWordResult.None
        }

        val t0 = 10_000_000_000L
        val res1 = checkWakeWord(true, "Hey Tara", t0)
        assertTrue(res1 is WakeWordResult.Detected)
        assertEquals("Hey Tara", (res1 as WakeWordResult.Detected).phrase)

        // During cooldown: suppressed
        val res2 = checkWakeWord(true, "Hey Tara", t0 + 1_000_000_000L)
        assertTrue(res2 is WakeWordResult.InCooldown)

        // After cooldown: ready again
        val res3 = checkWakeWord(true, "Hey Tara", t0 + 2_500_000_000L)
        assertTrue(res3 is WakeWordResult.Detected)
    }
}
