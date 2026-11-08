package dev.voice.offline.contacts

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

enum class PhoneType {
    ANY,
    MOBILE,
    HOME,
    WORK,
    OTHER
}

data class ContactNumber(
    val number: String,
    val type: PhoneType,
    val label: String? = null
)

data class ContactRecord(
    val contactId: String,
    val displayName: String,
    val numbers: List<ContactNumber>
)

sealed interface ContactResolution {
    data class DirectNumber(val phoneNumber: String) : ContactResolution
    data class Resolved(val contact: ContactRecord, val selectedNumber: String) : ContactResolution
    data class Ambiguous(val candidates: List<ContactRecord>, val query: String) : ContactResolution
    data class NotFound(val query: String) : ContactResolution
}

/**
 * On-device contact resolver integrating with Android ContactsContract.
 * Resolves spoken contact names, phone type qualifiers ("on mobile", "at work"),
 * and raw phone numbers without sending data off-device.
 */
class ContactResolver(
    private val context: Context? = null,
    private val queryProvider: ((String) -> List<ContactRecord>)? = null
) {
    companion object {
        private val PHONE_NUMBER_REGEX = Regex("""^\+?[0-9\s\-().]{3,25}$""")
    }

    fun resolve(rawQuery: String): ContactResolution {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return ContactResolution.NotFound("")
        }

        // 1. Check if raw query is already a phone number
        if (isPhoneNumber(trimmed)) {
            val clean = normalizeDigits(trimmed)
            return ContactResolution.DirectNumber(clean)
        }

        // 2. Parse name and preferred phone type (e.g. "Rahul on mobile", "Mom at home")
        val (contactName, preferredType) = parseQueryType(trimmed)

        // 3. Query contacts
        val candidates = if (queryProvider != null) {
            queryProvider.invoke(contactName)
        } else {
            queryContactsContract(contactName)
        }

        if (candidates.isEmpty()) {
            return ContactResolution.NotFound(contactName)
        }

        // 4. Exact match precedence
        val exactMatches = candidates.filter { it.displayName.equals(contactName, ignoreCase = true) }
        val pool = if (exactMatches.isNotEmpty()) exactMatches else candidates

        // 5. Handle ambiguity
        if (pool.size > 1) {
            return ContactResolution.Ambiguous(pool, contactName)
        }

        // Exactly 1 match found
        val contact = pool.first()
        if (contact.numbers.isEmpty()) {
            return ContactResolution.NotFound(contactName)
        }

        // 6. Select appropriate phone number based on preferred type
        val selectedNumber = selectNumber(contact.numbers, preferredType)
        return ContactResolution.Resolved(contact, selectedNumber)
    }

    private fun isPhoneNumber(query: String): Boolean {
        val digitsCount = query.count { it.isDigit() }
        return digitsCount >= 3 && PHONE_NUMBER_REGEX.matches(query)
    }

    private fun normalizeDigits(raw: String): String {
        val hasPlus = raw.startsWith("+")
        val digits = raw.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    private fun parseQueryType(query: String): Pair<String, PhoneType> {
        val mobileRegex = Regex("""^(.*?)\s+(?:on\s+|at\s+)?mobile$""", RegexOption.IGNORE_CASE)
        val homeRegex = Regex("""^(.*?)\s+(?:on\s+|at\s+)?home$""", RegexOption.IGNORE_CASE)
        val workRegex = Regex("""^(.*?)\s+(?:on\s+|at\s+)?work$""", RegexOption.IGNORE_CASE)

        mobileRegex.find(query)?.let { return Pair(it.groupValues[1].trim(), PhoneType.MOBILE) }
        homeRegex.find(query)?.let { return Pair(it.groupValues[1].trim(), PhoneType.HOME) }
        workRegex.find(query)?.let { return Pair(it.groupValues[1].trim(), PhoneType.WORK) }

        return Pair(query.trim(), PhoneType.ANY)
    }

    private fun selectNumber(numbers: List<ContactNumber>, preferred: PhoneType): String {
        if (preferred != PhoneType.ANY) {
            val matching = numbers.firstOrNull { it.type == preferred }
            if (matching != null) return matching.number
        }
        // Default: mobile first, then first available
        val mobile = numbers.firstOrNull { it.type == PhoneType.MOBILE }
        return mobile?.number ?: numbers.first().number
    }

    private fun queryContactsContract(query: String): List<ContactRecord> {
        val ctx = context ?: return emptyList()
        val resolver = ctx.contentResolver
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL
        )

        val map = mutableMapOf<String, ContactRecordBuilder>()
        val selection = "${Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            resolver.query(
                Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Phone.DISPLAY_NAME_PRIMARY + " ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(Phone.LABEL)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val typeInt = if (typeIdx >= 0) cursor.getInt(typeIdx) else Phone.TYPE_OTHER
                    val label = if (labelIdx >= 0) cursor.getString(labelIdx) else null

                    if (id.isBlank() || name.isBlank() || num.isBlank()) continue

                    val type = when (typeInt) {
                        Phone.TYPE_MOBILE -> PhoneType.MOBILE
                        Phone.TYPE_HOME -> PhoneType.HOME
                        Phone.TYPE_WORK -> PhoneType.WORK
                        else -> PhoneType.OTHER
                    }

                    val builder = map.getOrPut(id) { ContactRecordBuilder(id, name) }
                    builder.numbers.add(ContactNumber(num, type, label))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return map.values.map { it.build() }
    }

    private class ContactRecordBuilder(val id: String, val name: String) {
        val numbers = mutableListOf<ContactNumber>()
        fun build() = ContactRecord(id, name, numbers.toList())
    }
}

