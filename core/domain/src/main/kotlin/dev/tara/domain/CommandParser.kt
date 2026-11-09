package dev.tara.domain

sealed interface ParseResult {
    data class Ready(val action: TaraAction) : ParseResult
    data class ResolveApp(val name: String) : ParseResult
    data class ResolveContact(val name: String, val smsBody: String? = null) : ParseResult
    data class Clarification(val message: String) : ParseResult
}
fun interface CommandParser { fun parse(text: String): ParseResult }
class DeterministicCommandParser : CommandParser {
    override fun parse(text: String): ParseResult {
        val command = text.trim()
        if (command.length > 2000) return ParseResult.Clarification("Command is too long.")
        val c = command.lowercase()
        when (c) {
            "lock phone", "lock device", "lock my phone" -> return ParseResult.Ready(TaraAction.LockDevice)
            "flashlight on", "torch on", "turn on flashlight" -> return ParseResult.Ready(TaraAction.SetTorch(true))
            "flashlight off", "torch off", "turn off flashlight" -> return ParseResult.Ready(TaraAction.SetTorch(false))
            "volume up", "increase volume" -> return ParseResult.Ready(TaraAction.ChangeVolume(VolumeDirection.UP))
            "volume down", "decrease volume" -> return ParseResult.Ready(TaraAction.ChangeVolume(VolumeDirection.DOWN))
        }
        Regex("(?i)(?:call|dial) (.+)").matchEntire(command)?.let { m ->
            val raw = m.groupValues[1].trim()
            return if (raw.any(Char::isDigit) || raw.startsWith("+") || raw.contains('*') || raw.contains('#'))
                ParseResult.Ready(TaraAction.DialNumber(PhoneNumber(normalizePhone(raw))))
            else ParseResult.ResolveContact(raw)
        }
        Regex("(?is)(?:sms|text|message) (.+?) (?:saying|message) (.*)").matchEntire(command)?.let { m ->
            val recipient = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            return if (recipient.any(Char::isDigit) || recipient.contains('*') || recipient.contains('#'))
                ParseResult.Ready(TaraAction.ComposeSms(PhoneNumber(normalizePhone(recipient)), body))
            else ParseResult.ResolveContact(recipient, body)
        }
        Regex("(?i)(?:open|launch) (.+)").matchEntire(command)?.let { return ParseResult.ResolveApp(it.groupValues[1].trim()) }
        Regex("(?i)(?:set )?timer (?:for )?(\\d+) (seconds?|minutes?|hours?)").matchEntire(command)?.let { m ->
            val multiplier = when { m.groupValues[2].startsWith("hour", true) -> 3600L; m.groupValues[2].startsWith("minute", true) -> 60L; else -> 1L }
            val value = m.groupValues[1].toLongOrNull()
            if (value == null || value !in 1..(86400 / multiplier)) return ParseResult.Clarification("Timer duration must be between 1 second and 24 hours.")
            return ParseResult.Ready(TaraAction.SetTimer((value * multiplier).toInt()))
        }
        Regex("(?i)(?:set )?alarm (?:for |at )?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matchEntire(command)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            val meridiem = m.groupValues[3].lowercase()
            if (meridiem.isEmpty() && hour in 1..12) return ParseResult.Clarification("Specify AM or PM, or an unambiguous 24-hour time such as 19:30.")
            if (meridiem.isNotEmpty()) {
                if (hour !in 1..12) return ParseResult.Clarification("AM/PM hours must be 1–12.")
                hour = hour % 12 + if (meridiem == "pm") 12 else 0
            }
            return ParseResult.Ready(TaraAction.SetAlarm(hour, minute))
        }
        return ParseResult.Clarification("Try: open Camera, call a number, sms a number saying your message, flashlight on, volume down, alarm for 7 am, or timer for 5 minutes.")
    }
    private fun normalizePhone(value: String) = value.replace(Regex("[ ()-]"), "")
}
