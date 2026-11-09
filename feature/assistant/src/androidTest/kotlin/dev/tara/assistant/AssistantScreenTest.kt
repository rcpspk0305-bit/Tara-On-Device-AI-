package dev.tara.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.tara.designsystem.TaraTheme
import dev.tara.domain.ActionResult
import dev.tara.domain.AssistantState
import dev.tara.domain.Capability
import dev.tara.domain.PhoneNumber
import dev.tara.domain.SessionState
import dev.tara.domain.TaraAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AssistantScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmationShowsExactRecipientAndBodyAndCanCancel() {
        val action = TaraAction.ComposeSms(PhoneNumber("9876543210"), "Please meet me at 6:30.")
        var confirmed = 0
        var cancelled = 0
        render(
            state = SessionState(AssistantState.AwaitingConfirmation, action = action, result = ActionResult.ConfirmationRequired(action, "Review")),
            onConfirm = { confirmed++ },
            onCancel = { cancelled++ },
        )
        compose.onNodeWithTag("confirmation_details").assertTextContains("9876543210", substring = true)
        compose.onNodeWithTag("confirmation_details").assertTextContains("Please meet me at 6:30.", substring = true)
        assertEquals(0, confirmed)
        compose.onNodeWithTag("cancel_confirmation").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    @Test fun listeningExposesImmediateStopAndDisablesExecution() {
        var stopped = 0
        render(SessionState(AssistantState.Listening, message = "Listening"), onStop = { stopped++ })
        compose.onNodeWithText("Stop listening").assertIsDisplayed()
        compose.onNodeWithTag("execute").assertIsNotEnabled()
        compose.onNodeWithTag("microphone").performClick()
        assertEquals(1, stopped)
    }

    @Test fun productionUiHidesDeveloperPresetsAndShowsPermissionAction() {
        val requested = mutableListOf<Capability>()
        render(
            SessionState(AssistantState.AwaitingPermission, result = ActionResult.PermissionRequired(Capability.CAMERA, "Camera access required")),
            onPermission = requested::add,
        )
        compose.onNodeWithText("Developer command presets").assertDoesNotExist()
        compose.onNodeWithText("Review access").performScrollTo().performClick()
        assertEquals(listOf(Capability.CAMERA), requested)
    }

    private fun render(
        state: SessionState,
        onStop: () -> Unit = {},
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onPermission: (Capability) -> Unit = {},
    ) {
        compose.setContent {
            TaraTheme(false) {
                AssistantScreen(
                    state = state,
                    command = "volume down",
                    onCommandChange = {},
                    onExecute = {},
                    onListen = {},
                    onStop = onStop,
                    onCancel = onCancel,
                    onConfirm = onConfirm,
                    onChoose = {},
                    onPermission = onPermission,
                    onPickContact = {},
                    debug = false,
                )
            }
        }
    }
}
