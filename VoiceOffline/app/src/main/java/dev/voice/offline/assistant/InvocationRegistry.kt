package dev.voice.offline.assistant

import java.util.concurrent.ConcurrentHashMap

/** A dismissal can revoke an invocation before Android has delivered the start intent. */
class InvocationRegistry {
    private val invocations = ConcurrentHashMap.newKeySet<String>()
    fun register(id: String) { invocations.add(id) }
    fun mayStart(id: String): Boolean = invocations.contains(id)
    fun cancel(id: String) { invocations.remove(id) }
    fun clear() { invocations.clear() }
}
