package dev.voice.offline.tts

import org.junit.Assert.*
import org.junit.Test

class OfflineTextToSpeechEngineTest {

    private class MockTtsSpeaker : TtsSpeaker {
        val spokenUtterances = mutableListOf<String>()
        var isReady = true
        var wasStopped = false
        var wasShutdown = false

        override fun isAvailable(): Boolean = isReady

        override fun speak(text: String, flush: Boolean, onComplete: (() -> Unit)?): Boolean {
            if (!isReady) return false
            if (flush) spokenUtterances.clear()
            spokenUtterances.add(text)
            onComplete?.invoke()
            return true
        }

        override fun stop() {
            wasStopped = true
        }

        override fun shutdown() {
            wasShutdown = true
        }
    }

    @Test
    fun testSpeakOutputsConfirmationAndCallsCompletion() {
        val mock = MockTtsSpeaker()
        val engine = OfflineTextToSpeechEngine(speaker = mock)

        var completed = false
        val spoken = engine.speak("Screen locked", flush = true) {
            completed = true
        }

        assertTrue("Speak should succeed", spoken)
        assertTrue("Completion callback must be invoked", completed)
        assertEquals(listOf("Screen locked"), mock.spokenUtterances)
    }

    @Test
    fun testExplainFailureSpokenCorrectly() {
        val mock = MockTtsSpeaker()
        val engine = OfflineTextToSpeechEngine(speaker = mock)

        engine.explainFailure("Device administrator permission is not active.")

        assertEquals(listOf("Device administrator permission is not active."), mock.spokenUtterances)
    }

    @Test
    fun testStopAndShutdownReleasesResources() {
        val mock = MockTtsSpeaker()
        val engine = OfflineTextToSpeechEngine(speaker = mock)

        engine.stop()
        assertTrue(mock.wasStopped)

        engine.close()
        assertTrue(mock.wasShutdown)
    }

    @Test
    fun testSpeakFailsGracefullyWhenTtsNotReady() {
        val mock = MockTtsSpeaker()
        mock.isReady = false
        val engine = OfflineTextToSpeechEngine(speaker = mock)

        var callbackCalled = false
        val spoken = engine.speak("Test text") {
            callbackCalled = true
        }

        assertFalse("Should return false when TTS is not ready", spoken)
        assertFalse("Callback should not be called on immediate failure", callbackCalled)
    }
}

