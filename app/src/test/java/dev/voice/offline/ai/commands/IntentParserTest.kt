package dev.voice.offline.ai.commands

import org.junit.Assert.*
import org.junit.Test

class IntentParserTest {
    private val parser = IntentParser()
    @Test fun parsesSupportedActions() {
        assertEquals(VoiceCommand.LockScreen, parser.parse("lock my phone"))
        assertEquals(VoiceCommand.Call("alice"), parser.parse("Call Alice"))
        assertEquals(VoiceCommand.Reply("alice", "hello there"),
            parser.parse("reply to alice saying hello there"))
        assertEquals(VoiceCommand.Note("buy milk"), parser.parse("take a note buy milk"))
    }
    @Test fun parsesCommandsWithHeyTaraPrefix() {
        assertEquals(VoiceCommand.OpenCamera, parser.parse("Hey Tara, open camera"))
        assertEquals(VoiceCommand.LockScreen, parser.parse("Hey Tara lock my phone"))
        assertEquals(VoiceCommand.Call("alice"), parser.parse("Hey Tara, call Alice"))
        assertEquals(VoiceCommand.Note("buy groceries"), parser.parse("Okay Tara, take a note buy groceries"))
        assertEquals(VoiceCommand.OpenCamera, parser.parse("Tara launch camera"))
    }
    @Test fun doesNotStripNonWakeWordsContainingTaraOrVoice() {
        assertNull(parser.parse("tarzan call alice"))
        assertNull(parser.parse("voices in my head"))
    }
    @Test fun rejectsUnsupportedOrAmbiguousCommands() {
        assertNull(parser.parse("transfer money to bob"))
        assertNull(parser.parse("call"))
        assertNull(parser.parse("text mom hello"))
        assertNull(parser.parse("open my banking app"))
    }
}
