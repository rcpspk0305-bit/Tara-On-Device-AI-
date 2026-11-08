package dev.voice.offline.actions.navigation

import dev.voice.offline.actions.ExecutionResult
import org.junit.Assert.*
import org.junit.Test

class NavigationActionExecutorTest {

    @Test
    fun testNavigateHomeTriggersHomeLauncher() {
        var homeLaunched = false
        val executor = NavigationActionExecutor(homeLauncher = { homeLaunched = true })

        val result = executor.navigateHome()
        assertTrue(result is ExecutionResult.Success)
        assertEquals("Navigated to Home screen", result.message)
        assertTrue(homeLaunched)
    }

    @Test
    fun testNavigateHomeExceptionHandledGracefully() {
        val executor = NavigationActionExecutor(homeLauncher = { error("Activity launch restricted") })

        val result = executor.navigateHome()
        assertTrue(result is ExecutionResult.Failed)
        assertTrue(result.message.contains("Could not navigate home"))
    }
}
