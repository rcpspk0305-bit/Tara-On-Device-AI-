package dev.voice.offline.actions.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dev.voice.offline.actions.ExecutionResult

class FlashlightActionExecutor(private val context: Context) {

    fun setTorchMode(enabled: Boolean): ExecutionResult {
        val manager = context.getSystemService(CameraManager::class.java)
            ?: return ExecutionResult.Failed("Camera service unavailable")

        return try {
            val cameraId = findTorchCameraId(manager)
                ?: return ExecutionResult.Failed("No device flashlight/torch detected")

            manager.setTorchMode(cameraId, enabled)
            val state = if (enabled) "on" else "off"
            ExecutionResult.Success("Flashlight turned $state")
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to control flashlight: ${e.message}")
        }
    }

    private fun findTorchCameraId(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return manager.cameraIdList.firstOrNull()
    }
}
