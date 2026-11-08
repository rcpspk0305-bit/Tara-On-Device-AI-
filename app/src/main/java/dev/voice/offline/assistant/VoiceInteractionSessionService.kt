package dev.voice.offline.assistant

import android.os.Bundle

class VoiceInteractionSessionService : android.service.voice.VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): android.service.voice.VoiceInteractionSession =
        VoiceInteractionSession(this)
}
