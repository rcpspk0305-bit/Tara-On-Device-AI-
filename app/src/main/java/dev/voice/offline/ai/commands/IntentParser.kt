package dev.voice.offline.ai.commands

/** Exact patterns avoid silently turning uncertain speech into sensitive side effects. */
class IntentParser {
    fun parse(transcript: String): VoiceCommand? {
        val raw = transcript.trim().lowercase().replace(Regex("\\s+"), " ")
        val text = raw.replace(Regex("^(?:hey|okay|hi|hello)?\\s*\\b(?:tara|voice)\\b[,\\s:]*"), "").trim()
        if (text == "answer call" || text == "answer the call" || text == "pick up the phone")
            return VoiceCommand.AnswerCall
        if (text == "lock screen" || text == "lock my phone" || text == "lock the phone")
            return VoiceCommand.LockScreen
        if (text == "open camera" || text == "launch camera")
            return VoiceCommand.OpenCamera

        Regex("^(?:call|phone) ([a-z][a-z .'-]{0,69})$").matchEntire(text)?.let {
            return VoiceCommand.Call(it.groupValues[1].trim())
        }
        Regex("^(?:reply to|respond to) ([a-z][a-z .'-]{0,69}) (?:saying|with|that) (.{1,500})$")
            .matchEntire(text)?.let {
                return VoiceCommand.Reply(it.groupValues[1].trim(), it.groupValues[2].trim())
            }
        Regex("^(?:take a note|note down|remember this|make a note) (.{1,2000})$")
            .matchEntire(text)?.let { return VoiceCommand.Note(it.groupValues[1].trim()) }
        return null
    }
}
