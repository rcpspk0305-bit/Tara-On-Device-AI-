package dev.voice.offline.core.security

import dev.voice.offline.ai.functioncalling.StructuredAction
import org.junit.Assert.*
import org.junit.Test

class SecurityPolicyTest {

    @Test
    fun externalActivitiesRequireUserInteractionWithoutRequiringBiometricsForLowRiskActions() {
        val policy = SensitiveActionPolicy()
        listOf(StructuredAction.OpenCamera, StructuredAction.OpenApp("com.example"),
            StructuredAction.MakeCall("Mom"), StructuredAction.SetAlarm(7, 0)).forEach {
            assertTrue(policy.requiresUserInteraction(it))
        }
        assertFalse(policy.requiresBiometrics(StructuredAction.OpenCamera))
        assertFalse(policy.requiresUserInteraction(StructuredAction.TakeNotes("Milk")))
        assertFalse(policy.requiresUserInteraction(StructuredAction.TurnFlashlightOn))
        assertTrue(policy.requiresUserInteraction(StructuredAction.LockScreen))
    }

    @Test
    fun screenLockRequiresBiometricsAndCannotBeDowngraded() {
        val policy = SensitiveActionPolicy()
        assertTrue(policy.requiresBiometrics(StructuredAction.LockScreen))
        policy.setOverride("lock_screen", ActionRiskLevel.LOW)
        policy.setOverride("text_msg", ActionRiskLevel.LOW)
        assertTrue(policy.requiresBiometrics(StructuredAction.LockScreen))
        assertTrue(policy.requiresBiometrics(StructuredAction.TextMsg("Mom", "Hello")))
    }

    @Test
    fun directScreenLockCanBeEnabledForHandsFreeDeviceAdminExecution() {
        val policy = SensitiveActionPolicy()
        assertTrue(policy.requiresUserInteraction(StructuredAction.LockScreen))
        policy.directScreenLockEnabled = true
        assertFalse(policy.requiresUserInteraction(StructuredAction.LockScreen))
    }

    @Test
    fun testDefaultActionRiskLevels() {
        val policy = SensitiveActionPolicy()

        // Low risk actions
        assertEquals(ActionRiskLevel.LOW, policy.getRiskLevel(StructuredAction.OpenCamera))
        assertEquals(ActionRiskLevel.LOW, policy.getRiskLevel(StructuredAction.TurnFlashlightOn))
        assertEquals(ActionRiskLevel.LOW, policy.getRiskLevel(StructuredAction.TurnFlashlightOff))
        assertEquals(ActionRiskLevel.LOW, policy.getRiskLevel(StructuredAction.TakeNotes("buy milk")))
        assertEquals(ActionRiskLevel.LOW, policy.getRiskLevel(StructuredAction.OpenApp("com.example.app")))

        // Medium risk actions
        assertEquals(ActionRiskLevel.MEDIUM, policy.getRiskLevel(StructuredAction.MakeCall("Amma")))
        assertEquals(ActionRiskLevel.MEDIUM, policy.getRiskLevel(StructuredAction.SetAlarm(8, 0, "Meeting")))
        assertEquals(ActionRiskLevel.MEDIUM, policy.getRiskLevel(StructuredAction.ReadNotifications))
        assertEquals(ActionRiskLevel.MEDIUM, policy.getRiskLevel(StructuredAction.AnswerCall))

        // High risk actions
        assertEquals(ActionRiskLevel.HIGH, policy.getRiskLevel(StructuredAction.TextMsg("Amma", "Hello")))
        assertEquals(ActionRiskLevel.HIGH, policy.getRiskLevel(StructuredAction.ReplyToNotification("id123", "Yes")))
    }

    @Test
    fun testConfigurablePolicyOverrides() {
        val policy = SensitiveActionPolicy()
        policy.setOverride("make_call", ActionRiskLevel.HIGH)
        assertEquals(ActionRiskLevel.HIGH, policy.getRiskLevel(StructuredAction.MakeCall("Amma")))

        policy.setOverride("turn_flashlight_on", ActionRiskLevel.MEDIUM)
        assertEquals(ActionRiskLevel.MEDIUM, policy.getRiskLevel(StructuredAction.TurnFlashlightOn))
    }

    @Test
    fun testPendingCommandStoreSingleUseAndReplayPrevention() {
        val store = PendingCommandStore()
        val action = StructuredAction.MakeCall("Amma")

        val token = store.enqueue(action)
        assertNotNull(token)

        // Peeking returns the command without removing it
        val peeked = store.peek(token)
        assertEquals(action, peeked)

        // Taking returns and consumes the token
        val taken = store.take(token)
        assertEquals(action, taken)

        // Replay attempt fails: token cannot be reused
        val replayed = store.take(token)
        assertNull("Replayed token must return null", replayed)
    }

    @Test
    fun testPendingCommandStoreExpiration() {
        val store = PendingCommandStore(ttlMs = 10L) // Short TTL for test
        val action = StructuredAction.LockScreen

        val token = store.enqueue(action)
        Thread.sleep(25L)

        val taken = store.take(token)
        assertNull("Expired token must return null", taken)
    }
}
