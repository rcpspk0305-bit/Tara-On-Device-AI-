package dev.voice.offline.service

import dev.voice.offline.ai.stt.CommandDeadline
import org.junit.Assert.*
import org.junit.Test

class CommandDeadlineTest {
    @Test fun initialSilenceEndsWithoutAnyRecognizedWords() {
        val deadline = CommandDeadline(7000, 3000, 100)
        assertFalse(deadline.hasExpired(3099))
        assertTrue(deadline.hasExpired(3100))
    }
    @Test fun unchangedPartialDoesNotExtendSilenceForever() {
        val deadline = CommandDeadline(7000, 2500, 0)
        deadline.onPartial("open", 500)
        deadline.onPartial("open", 2000)
        assertTrue(deadline.hasExpired(3000))
    }
    @Test fun continuousChangingPartialCannotBypassMaximumDuration() {
        val deadline = CommandDeadline(7000, 3000, 0)
        for (time in 1000L..7000L step 1000) deadline.onPartial("word $time", time)
        assertTrue(deadline.hasExpired(7000))
    }
}
