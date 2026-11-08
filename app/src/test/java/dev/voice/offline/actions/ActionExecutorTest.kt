package dev.voice.offline.actions

import dev.voice.offline.ai.functioncalling.StructuredAction
import org.junit.Assert.*
import org.junit.Test

class ActionExecutorTest {

    @Test
    fun testAlarmActionValidation() {
        val validAlarm = StructuredAction.SetAlarm(7, 30, "Gym")
        assertEquals(7, validAlarm.hour)
        assertEquals(30, validAlarm.minute)
        assertEquals("Gym", validAlarm.label)
    }

    @Test
    fun testFlashlightActionResolution() {
        val turnOn = StructuredAction.TurnFlashlightOn
        val turnOff = StructuredAction.TurnFlashlightOff
        assertEquals("turn_flashlight_on", turnOn.functionName)
        assertEquals("turn_flashlight_off", turnOff.functionName)
    }

    @Test
    fun testContactDisambiguationLogic() {
        // Mock contacts data
        val contacts = listOf(
            "Amma" to "9876543210",
            "Amma Work" to "9876543211",
            "Appa" to "9123456789"
        )

        fun findMatchingPhones(name: String): List<String> {
            val exact = contacts.filter { it.first.equals(name, ignoreCase = true) }
            if (exact.isNotEmpty()) return exact.map { it.second }
            return contacts.filter { it.first.contains(name, ignoreCase = true) }.map { it.second }
        }

        val ammaMatches = findMatchingPhones("Amma")
        assertEquals(1, ammaMatches.size)
        assertEquals("9876543210", ammaMatches.first())

        val partialMatches = findMatchingPhones("Am")
        assertEquals(2, partialMatches.size) // Amma and Amma Work -> Ambiguous
    }

    @Test
    fun testExecutionResultVariants() {
        val success = ExecutionResult.Success("Action executed")
        assertTrue(success.isAccepted)
        assertEquals("Action executed", success.message)

        val perm = ExecutionResult.RequiresPermission("android.permission.CALL_PHONE", "Call permission required")
        assertFalse(perm.isAccepted)
        assertEquals("android.permission.CALL_PHONE", perm.permission)

        val auth = ExecutionResult.RequiresAuthentication("Biometric authentication required", StructuredAction.OpenCamera)
        assertFalse(auth.isAccepted)

        val amb = ExecutionResult.Ambiguous(listOf("Amma", "Amma Work"), "Multiple contacts match 'Amma'")
        assertFalse(amb.isAccepted)
        assertEquals(2, amb.candidates.size)
    }
}
