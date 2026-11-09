package dev.tara.app

import dev.tara.audio.AudioState
import dev.tara.audio.SpeechController
import dev.tara.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Speech : SpeechController {
        override val state = MutableStateFlow<AudioState>(AudioState.Idle)
        var cancelled = 0
        var started = 0
        override fun start() { started++; state.value = AudioState.Listening }
        override fun stop() { state.value = AudioState.Transcribing }
        override fun cancel() { cancelled++; state.value = AudioState.Idle }
        override fun close() { cancel() }
    }
    private class Permissions(var allowed: Boolean = true) : PermissionStatusRepository {
        override fun status(capability: Capability) = if (allowed) PermissionStatus.GRANTED else PermissionStatus.DENIED
        override fun refresh() = Unit
    }
    private class Resolver : EntityResolver {
        override suspend fun apps(name: String) = emptyList<TargetOption>()
        override suspend fun contacts(name: String) = emptyList<TargetOption>()
    }
    private fun model(speech: Speech, permissions: Permissions, handler: ActionHandler = ActionHandler { ActionResult.Completed("Done") }): AssistantViewModel {
        val executor = ActionExecutor(ActionValidator(), ActionPolicy(permissions), handler)
        return AssistantViewModel(AssistantSessionManager(DeterministicCommandParser(), Resolver(), executor, { 0 }), speech, permissions)
    }
    @Test fun deniedMicrophoneDoesNotStartCapture() = runTest {
        val speech = Speech()
        val model = model(speech, Permissions(false))
        model.listen()
        assertEquals(0, speech.started)
        assertEquals(AssistantState.AwaitingPermission, model.state.value.state)
    }
    @Test fun recognizedTranscriptWaitsForExecute() = runTest {
        val speech = Speech()
        var executions = 0
        val model = model(speech, Permissions(), ActionHandler { executions++; ActionResult.Completed("Done") })
        model.listen()
        runCurrent()
        speech.state.value = AudioState.Transcript("volume down")
        runCurrent()
        assertEquals("volume down", model.command.value)
        assertEquals(0, executions)
        model.execute()
        runCurrent()
        assertEquals(1, executions)
        assertEquals("volume down", model.state.value.transcript)
        assertEquals(AssistantState.Completed, model.state.value.state)
    }
    @Test fun backgroundingCancelsListeningAndPendingApproval() = runTest {
        val speech = Speech()
        val model = model(speech, Permissions())
        model.listen()
        model.interrupted()
        assertEquals(AssistantState.Cancelled, model.state.value.state)
        assertTrue(speech.cancelled > 0)
        model.edit("sms 9876543210 saying Hello")
        model.execute()
        runCurrent()
        assertEquals(AssistantState.AwaitingConfirmation, model.state.value.state)
        model.interrupted()
        assertEquals(AssistantState.Cancelled, model.state.value.state)
    }
    @Test fun changingMessageCancelsExistingConfirmation() = runTest {
        val model = model(Speech(), Permissions())
        model.edit("sms 9876543210 saying Hello")
        model.execute()
        runCurrent()
        model.edit("sms 9876543210 saying Changed")
        model.confirm()
        runCurrent()
        assertEquals(AssistantState.Cancelled, model.state.value.state)
    }
    @Test fun recreatedOwnerStartsWithoutSensitivePendingState() = runTest {
        val first = model(Speech(), Permissions())
        first.edit("sms 9876543210 saying Secret")
        first.execute()
        runCurrent()
        val recreated = model(Speech(), Permissions())
        assertEquals("", recreated.command.value)
        assertEquals(AssistantState.Idle, recreated.state.value.state)
        assertNull(recreated.state.value.action)
    }
}
