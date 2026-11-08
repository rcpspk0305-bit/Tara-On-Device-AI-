package dev.voice.offline.ui.features.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProEntitlementTest {

    private var inMemoryProStatus = false
    private lateinit var entitlementManager: BaseProEntitlementManager

    @Before
    fun setUp() {
        inMemoryProStatus = false
        entitlementManager = BaseProEntitlementManager(
            getStoredStatus = { inMemoryProStatus },
            setStoredStatus = { inMemoryProStatus = it }
        )
    }

    @Test
    fun testDefaultFreeTierConstraints() {
        assertFalse("Pro should be locked by default", entitlementManager.isProUnlocked())
        assertFalse("Custom sensitivity locked on free tier", entitlementManager.canAccessCustomSensitivity())
        assertFalse("Action chaining locked on free tier", entitlementManager.canAccessActionChaining())
        assertFalse("Extended history locked on free tier", entitlementManager.canAccessExtendedHistory())
    }

    @Test
    fun testManualUnlock() {
        entitlementManager.setProUnlocked(true)
        assertTrue("Pro should be unlocked", entitlementManager.isProUnlocked())
        assertTrue("Custom sensitivity accessible on Pro", entitlementManager.canAccessCustomSensitivity())
        assertTrue("Action chaining accessible on Pro", entitlementManager.canAccessActionChaining())
    }

    @Test
    fun testInvalidLicenseKeysRejected() {
        assertFalse("Empty key rejected", entitlementManager.verifyOfflineLicenseKey(""))
        assertFalse("Random string rejected", entitlementManager.verifyOfflineLicenseKey("ABCDEF-12345"))
        assertFalse("Invalid prefix rejected", entitlementManager.verifyOfflineLicenseKey("PRO-INVALID-KEY"))
        assertFalse("Pro must remain locked", entitlementManager.isProUnlocked())
    }

    @Test
    fun testCommunityEditionKeyAcceptedOffline() {
        val verified = entitlementManager.verifyOfflineLicenseKey("VOICEPRO-COMMUNITY-EDITION")
        assertTrue("Community edition key must pass offline verification", verified)
        assertTrue("Pro should be unlocked after valid key entry", entitlementManager.isProUnlocked())
    }

    @Test
    fun testTestUnlockedKeyAcceptedOffline() {
        val verified = entitlementManager.verifyOfflineLicenseKey("VOICEPRO-TEST-UNLOCKED")
        assertTrue("Test unlocked key must pass offline verification", verified)
        assertTrue("Pro should be unlocked after valid key entry", entitlementManager.isProUnlocked())
    }
}
