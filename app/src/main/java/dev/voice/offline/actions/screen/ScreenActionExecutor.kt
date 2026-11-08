package dev.voice.offline.actions.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.core.system.VoiceAdminReceiver

class ScreenActionExecutor(private val context: Context) {

    fun lockScreen(): ExecutionResult {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
            ?: return ExecutionResult.Failed("Device policy manager unavailable")
        val admin = ComponentName(context, VoiceAdminReceiver::class.java)

        if (!policy.isAdminActive(admin)) {
            return ExecutionResult.Failed("Screen lock requires activating Device Administrator in Voice settings")
        }

        return try {
            policy.lockNow()
            ExecutionResult.Success("Screen locked")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to lock screen: ${e.message}")
        }
    }
}
