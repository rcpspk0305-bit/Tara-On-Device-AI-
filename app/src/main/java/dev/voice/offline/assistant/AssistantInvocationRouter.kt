package dev.voice.offline.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import dev.voice.offline.service.VoiceAssistantService

/** Each system session can stop only the worker invocation carrying its own identifier. */
object AssistantInvocationRouter {
    internal val invocations = InvocationRegistry()

    fun start(context: Context, id: String, onComplete: (String) -> Unit): Boolean {
        if (!AssistantCapabilityChecker(context).microphoneGranted) return false
        invocations.register(id)
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                invocations.cancel(id)
                onComplete(resultData?.getString("message") ?: "Interaction ended")
            }
        }
        return try {
            context.startForegroundService(Intent(context, VoiceAssistantService::class.java)
                .setAction(VoiceAssistantService.ACTION_START)
                .putExtra(VoiceAssistantService.EXTRA_INVOCATION_ID, id)
                .putExtra(VoiceAssistantService.EXTRA_COMPLETION, receiver))
            true
        } catch (_: IllegalStateException) { invocations.cancel(id); false }
        catch (_: SecurityException) { invocations.cancel(id); false }
    }

    fun cancel(context: Context, id: String) {
        invocations.cancel(id)
        if (VoiceAssistantService.activeInvocationId == id) {
            context.stopService(Intent(context, VoiceAssistantService::class.java))
        }
    }

    fun onAssistantReplaced(context: Context) {
        invocations.clear()
        VoiceAssistantService.activeInvocationId?.let { cancel(context, it) }
    }
}
