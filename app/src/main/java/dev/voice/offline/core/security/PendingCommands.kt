package dev.voice.offline.core.security

import android.os.SystemClock
import dev.voice.offline.ai.commands.VoiceCommand
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Never serialize sensitive spoken content into notifications or PendingIntent extras. */
object PendingCommands {
    private const val TTL_MS = 30_000L
    private data class Request(val command: VoiceCommand, val expiresAt: Long)
    private val requests = ConcurrentHashMap<String, Request>()

    fun enqueue(command: VoiceCommand): String {
        val now = SystemClock.elapsedRealtime()
        requests.entries.removeIf { it.value.expiresAt <= now }
        return UUID.randomUUID().toString().also {
            requests[it] = Request(command, now + TTL_MS)
        }
    }
    fun peek(token: String): VoiceCommand? = requests[token]
        ?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.command
    fun take(token: String): VoiceCommand? = requests.remove(token)
        ?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.command
}
