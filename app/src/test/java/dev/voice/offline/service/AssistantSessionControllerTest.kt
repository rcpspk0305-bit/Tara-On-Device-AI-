package dev.voice.offline.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantSessionControllerTest {
    private class Resources : AssistantSessionResources {
        val events = mutableListOf<String>()
        var capture: suspend () -> String = { "open camera" }
        var process: suspend () -> Unit = {}
        override suspend fun prepare(mode: ListeningMode) { events += "prepare" }
        override suspend fun captureCommand(mode: ListeningMode, transition: (AssistantState) -> Unit): String {
            events += "capture"
            transition(AssistantState.LISTENING)
            return capture()
        }
        override fun stopCapture() { events += "release" }
        override suspend fun processTranscript(text: String, transition: (AssistantState) -> Unit) {
            assertEquals("release", events.last())
            events += "process"
            process()
        }
        override fun close() { events += "close" }
    }

    @Test fun onDemandReleasesBeforeProcessingAndReturnsIdle() = runTest {
        val resources = Resources()
        val controller = AssistantSessionController(resources)
        assertTrue(controller.run(ListeningMode.ON_DEMAND))
        assertEquals(1, resources.events.count { it == "capture" })
        assertEquals(1, resources.events.count { it == "process" })
        assertEquals("close", resources.events.last())
        assertEquals(AssistantState.IDLE, controller.state.value)
    }

    @Test fun silenceDoesNotExecuteAnything() = runTest {
        val resources = Resources().apply { capture = { "" } }
        AssistantSessionController(resources).run(ListeningMode.ON_DEMAND)
        assertFalse(resources.events.contains("process"))
        assertTrue(resources.events.contains("release"))
    }

    @Test fun repeatedActivationCannotOwnSecondCapture() = runTest {
        val resources = Resources().apply { capture = { awaitCancellation() } }
        val controller = AssistantSessionController(resources)
        val first = launch { controller.run(ListeningMode.ON_DEMAND) }
        runCurrent()
        assertFalse(controller.run(ListeningMode.ON_DEMAND))
        assertEquals(1, resources.events.count { it == "prepare" })
        first.cancelAndJoin()
        assertEquals(AssistantState.IDLE, controller.state.value)
        assertEquals("close", resources.events.last())
    }

    @Test fun handsFreeRestartsCaptureWithoutReloadingModels() = runTest {
        val secondCapture = CompletableDeferred<Unit>()
        val resources = Resources()
        resources.capture = {
            if (resources.events.count { it == "capture" } == 2) {
                secondCapture.complete(Unit)
                awaitCancellation()
            }
            "open camera"
        }
        val controller = AssistantSessionController(resources)
        val job = launch { controller.run(ListeningMode.HANDS_FREE) }
        secondCapture.await()
        assertEquals(1, resources.events.count { it == "prepare" })
        assertEquals(1, resources.events.count { it == "process" })
        job.cancelAndJoin()
        assertEquals(AssistantState.IDLE, controller.state.value)
    }

    @Test fun inferenceFailureStillClosesResources() = runTest {
        val resources = Resources().apply { process = { error("inference interrupted") } }
        val controller = AssistantSessionController(resources)
        try { controller.run(ListeningMode.ON_DEMAND); fail("Expected failure") }
        catch (_: IllegalStateException) { }
        assertEquals("close", resources.events.last())
        assertEquals(AssistantState.IDLE, controller.state.value)
    }

    @Test fun captureFailureReleasesAndClosesWithoutProcessing() = runTest {
        val resources = Resources().apply { capture = { error("permission revoked") } }
        val controller = AssistantSessionController(resources)
        try { controller.run(ListeningMode.ON_DEMAND); fail("Expected failure") }
        catch (_: IllegalStateException) { }
        assertFalse(resources.events.contains("process"))
        assertTrue(resources.events.contains("release"))
        assertEquals("close", resources.events.last())
        assertEquals(AssistantState.IDLE, controller.state.value)
    }

    @Test fun cancellingDuringProcessingDoesNotRestartHandsFreeCapture() = runTest {
        val resources = Resources().apply { process = { awaitCancellation() } }
        val controller = AssistantSessionController(resources)
        val job = launch { controller.run(ListeningMode.HANDS_FREE) }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, resources.events.count { it == "capture" })
        assertEquals("close", resources.events.last())
        assertEquals(AssistantState.IDLE, controller.state.value)
    }

    @Test fun transitionsDescribeOneOnDemandInteraction() = runTest {
        val states = mutableListOf<AssistantState>()
        AssistantSessionController(Resources(), states::add).run(ListeningMode.ON_DEMAND)
        assertEquals(listOf(AssistantState.ACTIVATING, AssistantState.LISTENING,
            AssistantState.RECOGNIZING, AssistantState.INFERENCING,
            AssistantState.CLEANUP, AssistantState.IDLE), states)
    }
}
