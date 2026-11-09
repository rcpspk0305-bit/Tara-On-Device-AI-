package dev.voice.offline.actions.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.core.system.VoiceAdminReceiver
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ScreenActionExecutorTest {

    private class FakeDpm(
        var adminActive: Boolean = true,
        var lockNowThrows: Throwable? = null
    ) {
        var locked = false
        fun isAdminActive(admin: ComponentName): Boolean = adminActive
        fun lockNow() {
            lockNowThrows?.let { throw it }
            locked = true
        }
    }

    @Test
    fun testLockScreenFailsWhenAdminNotActive() {
        val fake = FakeDpm(adminActive = false)
        val executor = ScreenActionExecutor(
            dpmProvider = { fake as Any },
            adminChecker = { fake.isAdminActive(it) },
            lockAction = { fake.lockNow() }
        )

        val result = executor.lockScreen()
        assertTrue("Must fail when admin is not active", result is ExecutionResult.Failed)
        assertTrue((result as ExecutionResult.Failed).message.contains("Device Administrator"))
        assertFalse(fake.locked)
    }

    @Test
    fun testLockScreenSucceedsWhenAdminActive() {
        val fake = FakeDpm(adminActive = true)
        val executor = ScreenActionExecutor(
            dpmProvider = { fake as Any },
            adminChecker = { fake.isAdminActive(it) },
            lockAction = { fake.lockNow() }
        )

        val result = executor.lockScreen()
        assertTrue("Must succeed when admin is active", result is ExecutionResult.Success)
        assertEquals("Screen locked", (result as ExecutionResult.Success).message)
        assertTrue(fake.locked)
    }

    @Test
    fun testLockScreenHandlesRevokedPrivilegesSafely() {
        val fake = FakeDpm(adminActive = true, lockNowThrows = SecurityException("Admin privileges revoked"))
        val executor = ScreenActionExecutor(
            dpmProvider = { fake as Any },
            adminChecker = { fake.isAdminActive(it) },
            lockAction = { fake.lockNow() }
        )

        val result = executor.lockScreen()
        assertTrue("Must fail when lockNow throws SecurityException", result is ExecutionResult.Failed)
        assertTrue((result as ExecutionResult.Failed).message.contains("Device administrator privilege revoked or denied"))
    }

    @Test
    fun testLockScreenFailsWhenDpmUnavailable() {
        val executor = ScreenActionExecutor(
            dpmProvider = { null },
            adminChecker = { false },
            lockAction = {}
        )

        val result = executor.lockScreen()
        assertTrue("Must fail when DPM is unavailable", result is ExecutionResult.Failed)
        assertTrue((result as ExecutionResult.Failed).message.contains("Device policy manager unavailable"))
    }
}

