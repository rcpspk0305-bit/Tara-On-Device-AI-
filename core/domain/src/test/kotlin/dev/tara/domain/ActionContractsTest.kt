package dev.tara.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActionContractsTest {
    private val validator = ActionValidator()
    private val granted = object : PermissionStatusRepository {
        override fun status(capability: Capability) = PermissionStatus.GRANTED
        override fun refresh() = Unit
    }
    private fun executor(handler: ActionHandler, permissions: PermissionStatusRepository = granted) = ActionExecutor(validator, ActionPolicy(permissions), handler)
    private val actions = listOf(
        TaraAction.LockDevice, TaraAction.DialNumber(PhoneNumber("+919876543210")),
        TaraAction.ComposeSms(PhoneNumber("9876543210"), "On my way"), TaraAction.OpenApp(AppId("com.example.camera")),
        TaraAction.SetTorch(true), TaraAction.ChangeVolume(VolumeDirection.UP), TaraAction.SetAlarm(7, 30), TaraAction.SetTimer(300),
    )

    @Test fun `all eight typed actions have valid examples`() { actions.forEach { assertNull(it.preview(), validator.validate(it)) } }
    @Test fun `invalid numbers and telephony control codes are rejected`() {
        listOf("", "+", "12", "1".repeat(16), "tel:123456", "123;456", "*#06#", "123,456", "911\n", "１２３４５６", "123/456").forEach {
            assertNotNull(it, validator.validate(TaraAction.DialNumber(PhoneNumber(it))))
            assertNotNull(it, validator.validate(TaraAction.ComposeSms(PhoneNumber(it), "Hello")))
        }
    }
    @Test fun `blank and oversized messages rejected`() {
        listOf("", "  ", "x".repeat(1601), "abc\u0000").forEach { assertNotNull(validator.validate(TaraAction.ComposeSms(PhoneNumber("9876543210"), it))) }
    }
    @Test fun `alarm bounds checked`() {
        listOf(TaraAction.SetAlarm(-1, 0), TaraAction.SetAlarm(24, 0), TaraAction.SetAlarm(7, -1), TaraAction.SetAlarm(7, 60)).forEach { assertNotNull(validator.validate(it)) }
        assertNull(validator.validate(TaraAction.SetAlarm(23, 59)))
        assertNull(validator.validate(TaraAction.SetAlarm(0, 0)))
    }
    @Test fun `timer bounds checked`() {
        listOf(-1, 0, 86401, Int.MAX_VALUE).forEach { assertNotNull(validator.validate(TaraAction.SetTimer(it))) }
        assertNull(validator.validate(TaraAction.SetTimer(86400)))
    }
    @Test fun `package identifiers validated`() {
        listOf("", "camera", "com.app;rm", "https://example.org", "com.app/Activity").forEach { assertNotNull(validator.validate(TaraAction.OpenApp(AppId(it)))) }
    }
    @Test fun `registry dispatches all eight actions exhaustively`() = runTest {
        val platform = mockk<PlatformActions>()
        val result = ActionResult.Completed("ok")
        coEvery { platform.lock() } returns result
        coEvery { platform.dial(any()) } returns result
        coEvery { platform.sms(any()) } returns result
        coEvery { platform.open(any()) } returns result
        coEvery { platform.torch(any()) } returns result
        coEvery { platform.volume(any()) } returns result
        coEvery { platform.alarm(any()) } returns result
        coEvery { platform.timer(any()) } returns result
        val registry = ActionRegistry(platform)
        actions.forEach { assertEquals(result, registry.handle(it)) }
        coVerify(exactly = 1) { platform.lock(); platform.dial(any()); platform.sms(any()); platform.open(any()); platform.torch(any()); platform.volume(any()); platform.alarm(any()); platform.timer(any()) }
    }
    @Test fun `invalid action never reaches handler`() = runTest {
        val handler = mockk<ActionHandler>()
        assertTrue(executor(handler).execute(TaraAction.SetTimer(0)) is ActionResult.Failure)
        coVerify(exactly = 0) { handler.handle(any()) }
    }
    @Test fun `all denied states block camera and lock`() = runTest {
        val handler = mockk<ActionHandler>()
        PermissionStatus.entries.filter { it != PermissionStatus.GRANTED && it != PermissionStatus.UNAVAILABLE }.forEach { denied ->
            val permissions = object : PermissionStatusRepository {
                override fun status(capability: Capability) = denied
                override fun refresh() = Unit
            }
            assertTrue(executor(handler, permissions).execute(TaraAction.SetTorch(true)) is ActionResult.PermissionRequired)
            assertTrue(executor(handler, permissions).execute(TaraAction.LockDevice, true) is ActionResult.PermissionRequired)
        }
        coVerify(exactly = 0) { handler.handle(any()) }
    }
    @Test fun `authorization is checked again after confirmation`() = runTest {
        var allowed = true
        val permissions = object : PermissionStatusRepository {
            override fun status(capability: Capability) = if (allowed) PermissionStatus.GRANTED else PermissionStatus.REVOKED
            override fun refresh() = Unit
        }
        val handler = mockk<ActionHandler>()
        val executor = executor(handler, permissions)
        assertTrue(executor.execute(TaraAction.LockDevice) is ActionResult.ConfirmationRequired)
        allowed = false
        assertTrue(executor.execute(TaraAction.LockDevice, true) is ActionResult.PermissionRequired)
        coVerify(exactly = 0) { handler.handle(any()) }
    }
    @Test fun `lock warns when no secure screen lock exists`() = runTest {
        val permissions = object : PermissionStatusRepository {
            override fun status(capability: Capability) = if (capability == Capability.SECURE_LOCK) PermissionStatus.DENIED else PermissionStatus.GRANTED
            override fun refresh() = Unit
        }
        assertEquals(Capability.SECURE_LOCK, (executor(ActionHandler { error("must not lock") }, permissions).execute(TaraAction.LockDevice, true) as ActionResult.PermissionRequired).capability)
    }
    @Test fun `SMS requires exact confirmation then reports composer handoff`() = runTest {
        val sms = actions[2]
        var handled: TaraAction? = null
        val executor = executor(ActionHandler { handled = it; ActionResult.ExternalHandoff("Composer opened; message not sent.") })
        val confirmation = executor.execute(sms) as ActionResult.ConfirmationRequired
        assertEquals(sms, confirmation.action)
        assertTrue(confirmation.message.contains("On my way"))
        assertNull(handled)
        assertTrue(executor.execute(sms, true) is ActionResult.ExternalHandoff)
        assertEquals(sms, handled)
    }
    @Test fun `handler failures and unsupported outcomes remain distinct`() = runTest {
        listOf(ActionResult.Unsupported("Torch unavailable"), ActionResult.Failure("Camera in use"), ActionResult.Unsupported("No dialer installed")).forEach { result ->
            assertEquals(result, executor(ActionHandler { result }).execute(TaraAction.SetTorch(true)))
        }
    }

    private class Resolver : EntityResolver {
        var appOptions = emptyList<TargetOption>()
        var contactOptions = emptyList<TargetOption>()
        override suspend fun apps(name: String) = appOptions
        override suspend fun contacts(name: String) = contactOptions
    }
    @Test fun `SMS confirmation cancellation never dispatches`() = runTest {
        var count = 0
        val manager = AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor(ActionHandler { count++; ActionResult.Completed("ok") }), { 0 })
        manager.submit("sms 9876543210 saying Hello")
        assertEquals(AssistantState.AwaitingConfirmation, manager.state.value.state)
        manager.cancel()
        manager.confirm()
        assertEquals(0, count)
        assertEquals(AssistantState.Cancelled, manager.state.value.state)
    }
    @Test fun `expired confirmation cannot be replayed`() = runTest {
        var now = 0L
        var count = 0
        val manager = AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor(ActionHandler { count++; ActionResult.Completed("ok") }), { now })
        manager.submit("sms 9876543210 saying Hello")
        now = 30_000
        manager.confirm()
        assertEquals(0, count)
        assertEquals(AssistantState.Cancelled, manager.state.value.state)
    }
    @Test fun `duplicate contacts and multiple numbers require exact choice`() = runTest {
        val resolver = Resolver().apply { contactOptions = listOf(TargetOption("9876543210", "Sam • mobile"), TargetOption("9876543211", "Sam • home")) }
        var actual: TaraAction? = null
        val manager = AssistantSessionManager(DeterministicCommandParser(), resolver, executor(ActionHandler { actual = it; ActionResult.ExternalHandoff("Dialer opened") }), { 0 })
        manager.submit("call Sam")
        assertTrue(manager.state.value.result is ActionResult.AmbiguousTarget)
        assertNull(actual)
        manager.choose("invented")
        assertNull(actual)
        manager.choose("9876543211")
        assertEquals(TaraAction.DialNumber(PhoneNumber("9876543211")), actual)
    }
    @Test fun `missing app fails without executing`() = runTest {
        val manager = AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor(ActionHandler { error("must not execute") }), { 0 })
        manager.submit("open Uninstalled app")
        assertEquals(AssistantState.Error, manager.state.value.state)
    }
    @Test fun `concurrent submissions rejected and cancelled resolution cannot execute`() = runTest {
        val gate = CompletableDeferred<List<TargetOption>>()
        val resolver = object : EntityResolver {
            override suspend fun apps(name: String) = gate.await()
            override suspend fun contacts(name: String) = emptyList<TargetOption>()
        }
        var calls = 0
        val manager = AssistantSessionManager(DeterministicCommandParser(), resolver, executor(ActionHandler { calls++; ActionResult.Completed("ok") }), { 0 })
        val pending = async { manager.submit("open Camera") }
        testScheduler.runCurrent()
        assertEquals(AssistantState.Resolving, manager.state.value.state)
        manager.submit("volume up")
        manager.cancel()
        gate.complete(listOf(TargetOption("com.example.camera", "Camera")))
        pending.await()
        assertEquals(0, calls)
        assertEquals(AssistantState.Cancelled, manager.state.value.state)
    }
    @Test fun `audio states support cancellation and reviewed transcript`() {
        val manager = AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor(ActionHandler { ActionResult.Completed("ok") }), { 0 })
        assertTrue(manager.listening())
        assertFalse(manager.listening())
        assertEquals(AssistantState.Listening, manager.state.value.state)
        manager.transcribing()
        assertEquals(AssistantState.Transcribing, manager.state.value.state)
        manager.audioReady("volume up")
        assertEquals(AssistantState.Idle, manager.state.value.state)
        assertEquals("volume up", manager.state.value.transcript)
    }
    @Test fun `starting microphone clears previous target expiry`() = runTest {
        var now = 0L
        val resolver = Resolver().apply { appOptions = listOf(TargetOption("com.first.camera", "Camera"), TargetOption("com.second.camera", "Camera")) }
        val manager = AssistantSessionManager(DeterministicCommandParser(), resolver, executor(ActionHandler { ActionResult.Completed("ok") }), { now })
        manager.submit("open Camera")
        now = 25_000
        assertTrue(manager.listening())
        now = 30_001
        manager.expire()
        assertEquals(AssistantState.Listening, manager.state.value.state)
    }
    @Test fun `unavailable hardware is not a permission request`() = runTest {
        val absent = object : PermissionStatusRepository {
            override fun status(capability: Capability) = PermissionStatus.UNAVAILABLE
            override fun refresh() = Unit
        }
        assertTrue(executor(ActionHandler { error("unreachable") }, absent).execute(TaraAction.LockDevice) is ActionResult.Unsupported)
        assertTrue(executor(ActionHandler { error("unreachable") }, absent).execute(TaraAction.SetTorch(true)) is ActionResult.Unsupported)
    }
    @Test fun `unexpected platform failure becomes failure result`() = runTest {
        assertTrue(executor(ActionHandler { throw IllegalStateException("sensitive argument omitted") }).execute(TaraAction.SetTimer(1)) is ActionResult.Failure)
    }
    @Test fun `typed commands never invent recognized transcripts`() = runTest {
        val manager = AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor(ActionHandler { ActionResult.Completed("ok") }), { 0 })
        manager.submit("volume up")
        assertEquals("", manager.state.value.transcript)
        manager.audioReady("volume down")
        manager.submit("volume up")
        assertEquals("volume down", manager.state.value.transcript)
    }
}
