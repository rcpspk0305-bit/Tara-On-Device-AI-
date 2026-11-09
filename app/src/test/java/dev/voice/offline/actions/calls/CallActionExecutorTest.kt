package dev.voice.offline.actions.calls

import android.content.Intent
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.contacts.ContactNumber
import dev.voice.offline.contacts.ContactRecord
import dev.voice.offline.contacts.ContactResolver
import dev.voice.offline.contacts.PhoneType
import org.junit.Assert.*
import org.junit.Test

class CallActionExecutorTest {

    private val sampleContacts = listOf(
        ContactRecord("1", "Mom", listOf(ContactNumber("9876543210", PhoneType.MOBILE))),
        ContactRecord("2", "Rahul Sharma", listOf(ContactNumber("9111111111", PhoneType.MOBILE))),
        ContactRecord("3", "Rahul Verma", listOf(ContactNumber("9222222222", PhoneType.MOBILE)))
    )

    private val resolver = ContactResolver(queryProvider = { q ->
        sampleContacts.filter { it.displayName.lowercase().contains(q.lowercase()) }
    })

    @Test
    fun testDialDirectNumberWithoutContactsPermission() {
        var launchedAction: String? = null
        var launchedUri: String? = null
        val executor = CallActionExecutor(
            resolver = resolver,
            hasContactsPermission = { false },
            hasCallPhonePermission = { false },
            callLauncher = { action, uri ->
                launchedAction = action
                launchedUri = uri
            }
        )

        val result = executor.makeCall(StructuredAction.MakeCall("9876543210"))
        assertTrue("Direct number should succeed even without READ_CONTACTS", result is ExecutionResult.Success)
        assertTrue(result.message.contains("9876543210"))
        assertEquals(Intent.ACTION_DIAL, launchedAction)
        assertEquals("tel:9876543210", launchedUri)
    }

    @Test
    fun testCallRequiresContactsPermissionForNameLookup() {
        val executor = CallActionExecutor(
            resolver = resolver,
            hasContactsPermission = { false },
            hasCallPhonePermission = { false },
            callLauncher = { _, _ -> }
        )

        val result = executor.makeCall(StructuredAction.MakeCall("Mom"))
        assertTrue("Named contact requires READ_CONTACTS", result is ExecutionResult.RequiresPermission)
        assertEquals("android.permission.READ_CONTACTS", (result as ExecutionResult.RequiresPermission).permission)
    }

    @Test
    fun testFallbackToDialerWhenCallPhonePermissionDenied() {
        var launchedAction: String? = null
        var launchedUri: String? = null
        val executor = CallActionExecutor(
            resolver = resolver,
            hasContactsPermission = { true },
            hasCallPhonePermission = { false },
            callLauncher = { action, uri ->
                launchedAction = action
                launchedUri = uri
            }
        )

        val result = executor.makeCall(StructuredAction.MakeCall("Mom"))
        assertTrue(result is ExecutionResult.Success)
        assertTrue(result.message.contains("Dialer opened"))
        assertEquals(Intent.ACTION_DIAL, launchedAction)
        assertEquals("tel:9876543210", launchedUri)
    }

    @Test
    fun testDirectCallWhenCallPhonePermissionGranted() {
        var launchedAction: String? = null
        var launchedUri: String? = null
        val executor = CallActionExecutor(
            resolver = resolver,
            hasContactsPermission = { true },
            hasCallPhonePermission = { true },
            callLauncher = { action, uri ->
                launchedAction = action
                launchedUri = uri
            }
        )

        val result = executor.makeCall(StructuredAction.MakeCall("Mom"))
        assertTrue(result is ExecutionResult.Success)
        assertTrue(result.message.contains("Calling Mom"))
        assertEquals(Intent.ACTION_CALL, launchedAction)
        assertEquals("tel:9876543210", launchedUri)
    }

    @Test
    fun testAmbiguousContactReturnsAmbiguousResult() {
        val executor = CallActionExecutor(
            resolver = resolver,
            hasContactsPermission = { true },
            hasCallPhonePermission = { true },
            callLauncher = { _, _ -> }
        )

        val result = executor.makeCall(StructuredAction.MakeCall("Rahul"))
        assertTrue("Multiple Rahul matches must return Ambiguous", result is ExecutionResult.Ambiguous)
        assertEquals(2, (result as ExecutionResult.Ambiguous).candidates.size)
    }
}
