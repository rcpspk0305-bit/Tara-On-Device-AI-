package dev.tara.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Phase1AppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun enterAssistant() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("welcome_continue").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("command_input").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("welcome_continue").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("welcome_continue").performScrollTo().performClick()
            compose.onNodeWithTag("permissions_continue").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("command_input").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun realAppParsesTypedCommandAndRequiresExactSmsApproval() {
        enterAssistant()
        compose.onNodeWithTag("command_input").performScrollTo().performTextReplacement("sms 9876543210 saying Exact local test")
        compose.onNodeWithTag("execute").performScrollTo().performClick()
        compose.onNodeWithTag("confirmation_details").assertTextContains("9876543210", substring = true)
        compose.onNodeWithTag("confirmation_details").assertTextContains("Exact local test", substring = true)
        compose.onNodeWithTag("cancel_confirmation").performClick()
        compose.onNodeWithTag("assistant_state").performScrollTo().assertTextEquals("Cancelled")
    }
    @Test fun recreationCancelsApprovalAndRetainsNoApprovalDialog() {
        enterAssistant()
        compose.onNodeWithTag("command_input").performScrollTo().performTextReplacement("sms 9876543210 saying Do not send")
        compose.onNodeWithTag("execute").performScrollTo().performClick()
        compose.onNodeWithTag("confirmation_details").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("confirmation_details").assertDoesNotExist()
        compose.onNodeWithTag("assistant_state").performScrollTo().assertTextEquals("Cancelled")
    }
    @Test fun mergedManifestExcludesUnrelatedSensitivePermissions() {
        val context = compose.activity
        val requested = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions?.toSet().orEmpty()
        assertEquals(emptySet<String>(), requested.intersect(setOf(
            "android.permission.INTERNET", "android.permission.SEND_SMS", "android.permission.READ_SMS",
            "android.permission.CALL_PHONE", "android.permission.READ_CONTACTS", "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.FOREGROUND_SERVICE_MICROPHONE",
        )))
    }
    @Test fun diagnosticMicrophoneReleasesItsActualRecordingOnCancel() {
        enterAssistant()
        org.junit.Assume.assumeFalse("Diagnostic capture is used when native recognition is unavailable",
            android.os.Build.VERSION.SDK_INT >= 31 && android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(compose.activity))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            compose.activity.packageName, android.Manifest.permission.RECORD_AUDIO)
        val audio = requireNotNull(compose.activity.getSystemService(android.media.AudioManager::class.java))
        compose.onNodeWithTag("microphone").performScrollTo().performClick()
        compose.waitUntil(8_000) { audio.activeRecordingConfigurations.isNotEmpty() }
        compose.onNodeWithTag("cancel_operation").performScrollTo().performClick()
        compose.waitUntil(5_000) { audio.activeRecordingConfigurations.isEmpty() }
        compose.onNodeWithTag("assistant_state").performScrollTo().assertTextEquals("Cancelled")
    }
    @Test fun typedInputDoesNotClaimSpeechRecognition() {
        enterAssistant()
        compose.onNodeWithTag("command_input").performScrollTo().performTextReplacement("intentionally unsupported command")
        compose.onNodeWithTag("execute").performScrollTo().performClick()
        compose.onNodeWithTag("assistant_state").performScrollTo().assertTextEquals("Idle")
        compose.onNodeWithTag("transcript").assertDoesNotExist()
    }
}
