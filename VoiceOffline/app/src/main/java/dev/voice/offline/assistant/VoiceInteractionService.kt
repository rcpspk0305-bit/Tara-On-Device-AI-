package dev.voice.offline.assistant

/** System keeps this service bound: it must never construct STT, models or AudioRecord. */
class VoiceInteractionService : android.service.voice.VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST or
            android.service.voice.VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
    }

    override fun onShutdown() {
        AssistantInvocationRouter.onAssistantReplaced(this)
        super.onShutdown()
    }
}
