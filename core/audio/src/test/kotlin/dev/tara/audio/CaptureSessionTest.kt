package dev.tara.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {
    @Test fun `cancellation releases microphone resource exactly once`() {
        val session = CaptureSession()
        var releases = 0
        val token = session.begin { releases++ }
        assertTrue(session.current(token))
        session.end()
        session.end()
        assertEquals(1, releases)
        assertFalse(session.current(token))
    }
    @Test fun `starting a new capture closes old capture and rejects its late result`() {
        val session = CaptureSession()
        var oldReleases = 0
        var newReleases = 0
        val oldToken = session.begin { oldReleases++ }
        val newToken = session.begin { newReleases++ }
        assertEquals(1, oldReleases)
        assertFalse(session.current(oldToken))
        assertTrue(session.current(newToken))
        session.end()
        assertEquals(1, newReleases)
    }
}
