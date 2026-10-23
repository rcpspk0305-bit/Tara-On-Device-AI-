package dev.voice.offline.service

/**
 * Observable runtime states for Voice assistant lifecycle and pipeline processing.
 */
sealed interface AssistantState {
    data object IDLE : AssistantState
    data object ACTIVATING : AssistantState
    data object WAKE_LISTENING : AssistantState
    data object RECOGNIZING : AssistantState
    data object VALIDATING : AssistantState
    data object CLEANUP : AssistantState
    data object STOPPED : AssistantState
    data object INITIALIZING : AssistantState
    data object LISTENING : AssistantState
    data object WAKE_DETECTED : AssistantState
    data object RECORDING_COMMAND : AssistantState
    data object TRANSCRIBING : AssistantState
    data object INFERENCING : AssistantState
    data object AUTHORIZING : AssistantState
    data object EXECUTING : AssistantState
    data object CONFIRMING : AssistantState
    data class ERROR(val message: String) : AssistantState
}
