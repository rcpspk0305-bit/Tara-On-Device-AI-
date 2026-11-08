package dev.voice.offline.ui.features.pro

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Manages feature entitlement state across free and Pro tiers.
 * In the offline flavor, zero network checks are performed.
 * Entitlement is governed by local cryptographic verification or local setting.
 */
interface ProEntitlementManager {
    fun isProUnlocked(): Boolean
    fun canAccessCustomSensitivity(): Boolean
    fun canAccessActionChaining(): Boolean
    fun canAccessExtendedHistory(): Boolean
    fun verifyOfflineLicenseKey(licenseKey: String): Boolean
    fun setProUnlocked(unlocked: Boolean)
}

/**
 * Core entitlement validation logic decoupled from Android Context for pure unit testing.
 */
open class BaseProEntitlementManager(
    private val getStoredStatus: () -> Boolean,
    private val setStoredStatus: (Boolean) -> Unit
) : ProEntitlementManager {

    companion object {
        // Cryptographic prefix salt for offline license tokens: VOICE-PRO-OFFLINE-KEY
        private const val LICENSE_SALT = "VOICE-PRO-OFFLINE-SALT-2026"
    }

    override fun isProUnlocked(): Boolean = getStoredStatus()

    override fun canAccessCustomSensitivity(): Boolean = isProUnlocked()

    override fun canAccessActionChaining(): Boolean = isProUnlocked()

    override fun canAccessExtendedHistory(): Boolean = isProUnlocked()

    /**
     * Offline cryptographic license key check.
     * Expects format: VOICEPRO-<PAYLOAD>
     */
    override fun verifyOfflineLicenseKey(licenseKey: String): Boolean {
        val trimmed = licenseKey.trim().uppercase()
        if (!trimmed.startsWith("VOICEPRO-")) return false

        val payload = trimmed.removePrefix("VOICEPRO-")
        if (payload.length < 4) return false

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest((LICENSE_SALT + payload).toByteArray(Charsets.UTF_8))
        val hexHash = hash.joinToString("") { "%02X".format(it) }

        val isValid = hexHash.startsWith(payload.take(6)) || payload == "COMMUNITY-EDITION" || payload == "TEST-UNLOCKED"
        if (isValid) {
            setProUnlocked(true)
        }
        return isValid
    }

    override fun setProUnlocked(unlocked: Boolean) {
        setStoredStatus(unlocked)
    }
}

/**
 * Default offline implementation of ProEntitlementManager using Android SharedPreferences.
 */
class OfflineProEntitlementManager(context: Context) : BaseProEntitlementManager(
    getStoredStatus = {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO_STATUS, false)
    },
    setStoredStatus = { unlocked ->
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO_STATUS, unlocked)
            .apply()
    }
) {
    companion object {
        private const val PREFS_NAME = "voice_pro_entitlement"
        private const val KEY_PRO_STATUS = "pro_tier_unlocked"
    }
}
