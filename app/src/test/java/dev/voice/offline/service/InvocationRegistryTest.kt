package dev.voice.offline.service

import dev.voice.offline.assistant.InvocationRegistry
import org.junit.Assert.*
import org.junit.Test

class InvocationRegistryTest {
    @Test fun dismissalBeforeServiceStartPreventsLateCapture() {
        val registry = InvocationRegistry()
        registry.register("first")
        registry.cancel("first")
        assertFalse(registry.mayStart("first"))
    }
    @Test fun cancellingOneInvocationCannotCancelAnother() {
        val registry = InvocationRegistry()
        registry.register("old")
        registry.register("new")
        registry.cancel("old")
        assertTrue(registry.mayStart("new"))
    }
    @Test fun replacingAssistantRevokesPendingInvocations() {
        val registry = InvocationRegistry()
        registry.register("pending")
        registry.clear()
        assertFalse(registry.mayStart("pending"))
    }
}
