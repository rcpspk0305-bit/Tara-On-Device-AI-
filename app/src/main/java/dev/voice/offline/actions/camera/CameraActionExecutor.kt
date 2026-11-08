package dev.voice.offline.actions.camera

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import dev.voice.offline.actions.ExecutionResult

class CameraActionExecutor(private val context: Context) {

    fun openCamera(): ExecutionResult {
        return try {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(cameraIntent)
            ExecutionResult.Success("Camera opened")
        } catch (e: Exception) {
            ExecutionResult.Failed("Could not open camera: ${e.message}")
        }
    }
}
