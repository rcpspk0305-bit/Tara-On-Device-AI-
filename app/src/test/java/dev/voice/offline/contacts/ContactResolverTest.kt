package dev.voice.offline.contacts

import org.junit.Assert.*
import org.junit.Test

class ContactResolverTest {

    private val sampleContacts = listOf(
        ContactRecord(
            contactId = "1",
            displayName = "Mom",
            numbers = listOf(
                ContactNumber("9876543210", PhoneType.MOBILE, "Mobile"),
                ContactNumber("01122334455", PhoneType.HOME, "Home")
            )
        ),
        ContactRecord(
            contactId = "2",
            displayName = "Rahul Sharma",
            numbers = listOf(
                ContactNumber("9111111111", PhoneType.MOBILE, "Mobile")
            )
        ),
        ContactRecord(
            contactId = "3",
            displayName = "Rahul Verma",
            numbers = listOf(
                ContactNumber("9222222222", PhoneType.MOBILE, "Mobile"),
                ContactNumber("9333333333", PhoneType.WORK, "Work")
            )
        ),
        ContactRecord(
            contactId = "4",
            displayName = "Dad",
            numbers = listOf(
                ContactNumber("9998887776", PhoneType.MOBILE, "Mobile")
            )
        )
    )

    private val resolver = ContactResolver(queryProvider = { query ->
        val clean = query.trim().lowercase()
        sampleContacts.filter { it.displayName.lowercase().contains(clean) }
    })

    @Test
    fun testDirectPhoneNumberResolution() {
        val result = resolver.resolve("9876543210")
        assertTrue("Direct digits must resolve to DirectNumber", result is ContactResolution.DirectNumber)
        assertEquals("9876543210", (result as ContactResolution.DirectNumber).phoneNumber)

        val formatted = resolver.resolve("+91-98765-43210")
        assertTrue(formatted is ContactResolution.DirectNumber)
        assertEquals("+919876543210", (formatted as ContactResolution.DirectNumber).phoneNumber)
    }

    @Test
    fun testExactContactMatch() {
        val result = resolver.resolve("Mom")
        assertTrue("Mom must resolve exactly", result is ContactResolution.Resolved)
        val resolved = result as ContactResolution.Resolved
        assertEquals("Mom", resolved.contact.displayName)
        // Default to mobile
        assertEquals("9876543210", resolved.selectedNumber)
    }

    @Test
    fun testContactWithTypeQualifier() {
        val homeResult = resolver.resolve("Mom on home")
        assertTrue(homeResult is ContactResolution.Resolved)
        assertEquals("01122334455", (homeResult as ContactResolution.Resolved).selectedNumber)

        val mobileResult = resolver.resolve("Mom on mobile")
        assertTrue(mobileResult is ContactResolution.Resolved)
        assertEquals("9876543210", (mobileResult as ContactResolution.Resolved).selectedNumber)
    }

    @Test
    fun testAmbiguousContactMatch() {
        val result = resolver.resolve("Rahul")
        assertTrue("Query 'Rahul' matches two contacts, must be Ambiguous", result is ContactResolution.Ambiguous)
        val ambiguous = result as ContactResolution.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        assertTrue(ambiguous.candidates.any { it.displayName == "Rahul Sharma" })
        assertTrue(ambiguous.candidates.any { it.displayName == "Rahul Verma" })
    }

    @Test
    fun testDisambiguatedByFullName() {
        val result = resolver.resolve("Rahul Verma on work")
        assertTrue(result is ContactResolution.Resolved)
        val resolved = result as ContactResolution.Resolved
        assertEquals("Rahul Verma", resolved.contact.displayName)
        assertEquals("9333333333", resolved.selectedNumber)
    }

    @Test
    fun testContactNotFound() {
        val result = resolver.resolve("Aunt Alice")
        assertTrue("Unknown contact must return NotFound", result is ContactResolution.NotFound)
        assertEquals("Aunt Alice", (result as ContactResolution.NotFound).query)
    }
}

