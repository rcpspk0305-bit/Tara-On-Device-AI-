package dev.voice.offline.ai.commands

sealed interface VoiceCommand {
    data class Call(val contact: String) : VoiceCommand
    data object AnswerCall : VoiceCommand
    data object LockScreen : VoiceCommand
    data object OpenCamera : VoiceCommand
    data class Reply(val sender: String, val message: String) : VoiceCommand
    data class Note(val content: String) : VoiceCommand
}
