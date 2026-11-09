package dev.tara.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.tara.designsystem.TaraTheme
import dev.tara.domain.Capability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun welcomeExplainsLimitsBeforeContinuation() {
        var continued = false
        compose.setContent { TaraTheme(false) { WelcomeScreen { continued = true } } }
        compose.onNodeWithText("Meet Tara").assertIsDisplayed()
        assertFalse(continued)
        compose.onNodeWithTag("welcome_continue").performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test fun permissionSetupIsExplicitAndOptional() {
        val requested = mutableListOf<Capability>()
        var continued = false
        compose.setContent {
            TaraTheme(false) {
                PermissionsOnboardingScreen(onContinue = { continued = true }, onPermission = requested::add)
            }
        }
        assertTrue(requested.isEmpty())
        compose.onNodeWithText("Set up microphone").performClick()
        assertEquals(listOf(Capability.MICROPHONE), requested)
        compose.onNodeWithTag("permissions_continue").performScrollTo().performClick()
        assertTrue(continued)
    }
}
