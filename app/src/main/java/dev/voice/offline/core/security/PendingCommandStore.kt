package dev.voice.offline.core.security

import android.os.SystemClock
import dev.voice.offline.ai.functioncalling.StructuredAction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory pending command queue with short-lived tokens (30s TTL).
 * Prevents serializing sensitive voice transcripts into notification extras
 * and ensures commands cannot be replayed or substituted.
 */
class PendingCommandStore(
    private val ttlMs: Long = 30_000L
) {
    companion object {
        val default = PendingCommandStore()
    }

    private data class Request(
        val action: StructuredAction,
        val expiresAt: Long
    )

    private val requests = ConcurrentHashMap<String, Request>()

    fun enqueue(action: StructuredAction): String {
        cleanupExpired()
        val token = UUID.randomUUID().toString()
        val now = currentTime()
        requests[token] = Request(action, now + ttlMs)
        return token
    }

    fun peek(token: String): StructuredAction? {
        val request = requests[token] ?: return null
        return if (request.expiresAt > currentTime()) {
            request.action
        } else {
            requests.remove(token)
            null
        }
    }

    /**
     * Atomically consumes the token, returning the action.
     * Prevents duplicate/replay attacks since the token is removed immediately.
     */
    fun take(token: String): StructuredAction? {
        val request = requests.remove(token) ?: return null
        return if (request.expiresAt > currentTime()) {
            request.action
        } else {
            null
        }
    }

    fun cleanupExpired() {
        val now = currentTime()
        requests.entries.removeIf { it.value.expiresAt <= now }
    }

    private fun currentTime(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}
