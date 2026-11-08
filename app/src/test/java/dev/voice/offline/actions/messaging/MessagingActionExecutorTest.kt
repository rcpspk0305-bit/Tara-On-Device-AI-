package dev.voice.offline.actions.messaging

import android.content.Intent
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.contacts.ContactNumber
import dev.voice.offline.contacts.ContactRecord
import dev.voice.offline.contacts.ContactResolver
import dev.voice.offline.contacts.PhoneType
import org.junit.Assert.*
import org.junit.Test

class MessagingActionExecutorTest {

    private val sampleContacts = listOf(
        ContactRecord("1", "Dad", listOf(ContactNumber("9998887776", PhoneType.MOBILE))),
        ContactRecord("2", "Rahul", listOf(ContactNumber("9111111111", PhoneType.MOBILE)))
    )

    private val resolver = ContactResolver(queryProvider = { q ->
        sampleContacts.filter { it.displayName.lowercase().contains(q.lowercase()) }
    })

    @Test
    fun testSendTextPreparesDraftMessageWithReadback() {
        var launchedNumber: String? = null
        var launchedBody: String? = null
        val executor = MessagingActionExecutor(
            resolver = resolver,
            hasContactsPermission = { true },
            smsLauncher = { number, body ->
                launchedNumber = number
                launchedBody = body
            }
        )

        val action = StructuredAction.TextMsg("Dad", "I reached college")
        val result = executor.sendText(action)

        assertTrue("Result must be success", result is ExecutionResult.Success)
        val msg = result.message
        assertTrue("Readback must include recipient Dad", msg.contains("Dad"))
        assertTrue("Readback must include body content", msg.contains("I reached college"))

        assertEquals("9998887776", launchedNumber)
        assertEquals("I reached college", launchedBody)
    }

    @Test
    fun testDirectPhoneNumberMessagingWithoutContacts() {
        var launchedNumber: String? = null
        var launchedBody: String? = null
        val executor = MessagingActionExecutor(
            resolver = resolver,
            hasContactsPermission = { false },
            smsLauncher = { number, body ->
                launchedNumber = number
                launchedBody = body
            }
        )

        val action = StructuredAction.TextMsg("9876543210", "See you soon")
        val result = executor.sendText(action)

        assertTrue(result is ExecutionResult.Success)
        assertEquals("9876543210", launchedNumber)
        assertEquals("See you soon", launchedBody)
    }

    @Test
    fun testMissingContactReturnsFailedResult() {
        val executor = MessagingActionExecutor(
            resolver = resolver,
            hasContactsPermission = { true },
            smsLauncher = { _, _ -> }
        )

        val action = StructuredAction.TextMsg("Unknown Person", "Hello")
        val result = executor.sendText(action)

        assertTrue(result is ExecutionResult.Failed)
        assertTrue(result.message.contains("Unknown Person"))
    }
}
