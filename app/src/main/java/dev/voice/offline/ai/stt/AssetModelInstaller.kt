package dev.voice.offline.ai.stt

import android.content.Context
import java.io.File

/** Copies prepackaged model files to private credential-encrypted app storage. Run off the UI thread. */
object AssetModelInstaller {
    fun install(context: Context): File {
        val root = File(context.filesDir, "vosk-model-v1")
        val marker = File(root, ".complete")
        if (marker.exists()) return root
        val assets = context.assets
        check(assets.list("vosk-model")?.contains("am") == true) {
            "Missing Vosk model. See app/src/main/assets/vosk-model/README.txt"
        }
        if (root.exists()) root.deleteRecursively()
        fun copyAsset(relative: String, destination: File) {
            val children = assets.list(relative).orEmpty()
            if (children.isEmpty()) {
                destination.parentFile?.mkdirs()
                assets.open(relative).use { source ->
                    destination.outputStream().use { target -> source.copyTo(target) }
                }
            } else {
                destination.mkdirs()
                for (child in children) copyAsset("$relative/$child", File(destination, child))
            }
        }
        try {
            copyAsset("vosk-model", root)
            marker.writeText("ready")
            return root
        } catch (e: Exception) {
            root.deleteRecursively()
            throw e
        }
    }
}
