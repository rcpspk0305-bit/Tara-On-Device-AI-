package dev.voice.offline.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Test

class AssistantStateMachineTest {

    @Test
    fun testAllStatesExistAndTransition() {
        val states = listOf(
            AssistantState.STOPPED,
            AssistantState.INITIALIZING,
            AssistantState.LISTENING,
            AssistantState.WAKE_DETECTED,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING,
            AssistantState.INFERENCING,
            AssistantState.AUTHORIZING,
            AssistantState.EXECUTING,
            AssistantState.CONFIRMING,
            AssistantState.ERROR("Microphone unavailable")
        )

        val flow = MutableStateFlow<AssistantState>(AssistantState.STOPPED)
        val readOnlyFlow = flow.asStateFlow()

        assertEquals(AssistantState.STOPPED, readOnlyFlow.value)

        flow.value = AssistantState.INITIALIZING
        assertEquals(AssistantState.INITIALIZING, readOnlyFlow.value)

        flow.value = AssistantState.LISTENING
        assertEquals(AssistantState.LISTENING, readOnlyFlow.value)

        flow.value = AssistantState.WAKE_DETECTED
        assertEquals(AssistantState.WAKE_DETECTED, readOnlyFlow.value)

        flow.value = AssistantState.RECORDING_COMMAND
        assertEquals(AssistantState.RECORDING_COMMAND, readOnlyFlow.value)

        flow.value = AssistantState.TRANSCRIBING
        assertEquals(AssistantState.TRANSCRIBING, readOnlyFlow.value)

        flow.value = AssistantState.INFERENCING
        assertEquals(AssistantState.INFERENCING, readOnlyFlow.value)

        flow.value = AssistantState.EXECUTING
        assertEquals(AssistantState.EXECUTING, readOnlyFlow.value)

        flow.value = AssistantState.CONFIRMING
        assertEquals(AssistantState.CONFIRMING, readOnlyFlow.value)

        // Return to listening
        flow.value = AssistantState.LISTENING
        assertEquals(AssistantState.LISTENING, readOnlyFlow.value)

        flow.value = AssistantState.ERROR("Test error")
        assertTrue(readOnlyFlow.value is AssistantState.ERROR)
        assertEquals("Test error", (readOnlyFlow.value as AssistantState.ERROR).message)
    }
}
