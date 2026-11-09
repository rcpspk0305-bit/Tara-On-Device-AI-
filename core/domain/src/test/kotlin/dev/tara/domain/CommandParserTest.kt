package dev.tara.domain

import org.junit.Assert.*
import org.junit.Test

class CommandParserTest {
    private val parser = DeterministicCommandParser()
    @Test fun `all eight commands parse to typed actions or explicit resolution`() {
        val cases = mapOf(
            "lock phone" to TaraAction.LockDevice,
            "call +91 98765-43210" to TaraAction.DialNumber(PhoneNumber("+919876543210")),
            "sms 9876543210 saying Meet me at 5 PM" to TaraAction.ComposeSms(PhoneNumber("9876543210"), "Meet me at 5 PM"),
            "flashlight on" to TaraAction.SetTorch(true),
            "volume down" to TaraAction.ChangeVolume(VolumeDirection.DOWN),
            "alarm for 7 am" to TaraAction.SetAlarm(7, 0),
            "timer for 5 minutes" to TaraAction.SetTimer(300),
        )
        cases.forEach { (text, action) -> assertEquals(text, ParseResult.Ready(action), parser.parse(text)) }
        assertEquals(ParseResult.ResolveApp("Camera"), parser.parse("open Camera"))
    }
    @Test fun `noon and midnight converted correctly`() {
        assertEquals(ParseResult.Ready(TaraAction.SetAlarm(12, 0)), parser.parse("alarm for 12 pm"))
        assertEquals(ParseResult.Ready(TaraAction.SetAlarm(0, 0)), parser.parse("alarm for 12 am"))
        assertEquals(ParseResult.Ready(TaraAction.SetAlarm(19, 30)), parser.parse("alarm at 19:30"))
    }
    @Test fun `ambiguous hours demand clarification`() {
        listOf("alarm for 7", "alarm at 12:30", "alarm tomorrow morning", "alarm for 15 pm").forEach { assertTrue(it, parser.parse(it) is ParseResult.Clarification) }
    }
    @Test fun `numeric overflow cannot create a valid timer`() {
        listOf("timer for 9999999999999999999999 hours", "timer for 2147483647 seconds", "timer for 0 minutes").forEach { assertTrue(parser.parse(it) is ParseResult.Clarification) }
    }
    @Test fun `names remain resolution requests and message case preserved`() {
        assertEquals(ParseResult.ResolveContact("Sam"), parser.parse("call Sam"))
        assertEquals(ParseResult.ResolveContact("Sam", "Meet at HOME"), parser.parse("text Sam saying Meet at HOME"))
    }
    @Test fun `unknown or chained commands never execute arbitrary tools`() {
        listOf("", "unlock phone", "send SMS silently", "volume up then lock phone", "{\"function\":\"root\"}", "x".repeat(2001)).forEach { assertTrue(parser.parse(it) is ParseResult.Clarification) }
    }
}
