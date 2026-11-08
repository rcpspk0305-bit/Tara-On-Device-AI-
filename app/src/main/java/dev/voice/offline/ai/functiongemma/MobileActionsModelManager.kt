package dev.voice.offline.ai.functiongemma

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Manages local storage, integrity verification (SHA-256), version compatibility,
 * and lifecycle of the FunctionGemma 270M Mobile Actions (.litertlm) model.
 */
object MobileActionsModelManager {

    const val MODEL_FILENAME = "functiongemma-270m.litertlm"
    const val EXPECTED_MODEL_VERSION = "270m-ft-mobile-actions-v1"

    fun getModelFile(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    fun isModelInstalled(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 0
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun verifyIntegrity(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val computed = calculateSha256(file)
        return computed.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun installModel(sourceStream: InputStream, targetFile: File) {
        val temp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            FileOutputStream(temp).use { fos ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
                fos.flush()
            }
            if (targetFile.exists()) targetFile.delete()
            check(temp.renameTo(targetFile)) { "Failed to atomically rename model file" }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
