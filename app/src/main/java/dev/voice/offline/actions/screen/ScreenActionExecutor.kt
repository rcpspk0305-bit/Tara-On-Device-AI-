package dev.voice.offline.actions.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.core.system.VoiceAdminReceiver

class ScreenActionExecutor(
    private val context: Context? = null,
    private val dpmProvider: () -> Any? = { context?.getSystemService(DevicePolicyManager::class.java) },
    private val adminChecker: (ComponentName) -> Boolean = { admin ->
        (dpmProvider() as? DevicePolicyManager)?.isAdminActive(admin) == true
    },
    private val lockAction: () -> Unit = {
        (dpmProvider() as? DevicePolicyManager)?.lockNow()
            ?: error("Device policy manager unavailable")
    }
) {
    constructor(context: Context) : this(
        context = context,
        dpmProvider = { context.getSystemService(DevicePolicyManager::class.java) },
        adminChecker = { admin -> context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(admin) == true },
        lockAction = { context.getSystemService(DevicePolicyManager::class.java)?.lockNow() ?: error("Device policy manager unavailable") }
    )

    fun lockScreen(): ExecutionResult {
        if (dpmProvider() == null) {
            return ExecutionResult.Failed("Device policy manager unavailable on this device")
        }
        val admin = if (context != null) ComponentName(context, VoiceAdminReceiver::class.java)
            else ComponentName("dev.voice.offline", "dev.voice.offline.core.system.VoiceAdminReceiver")

        if (!adminChecker(admin)) {
            return ExecutionResult.Failed("Screen lock requires activating Device Administrator in Voice settings")
        }

        return try {
            lockAction()
            ExecutionResult.Success("Screen locked")
        } catch (sec: SecurityException) {
            ExecutionResult.Failed("Device administrator privilege revoked or denied: ${sec.message}")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to lock screen: ${e.message}")
        }
    }
}
