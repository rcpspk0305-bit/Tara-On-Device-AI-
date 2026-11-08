package dev.voice.offline.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

enum class HardwareSecurityTier {
    STRONGBOX,
    TRUSTED_ENVIRONMENT,
    SOFTWARE,
    UNKNOWN
}

/**
 * Android Keystore manager for hardware-backed keys, biometric-bound signing,
 * and security level auditing.
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_SIGNING_ALIAS = "voice-approval-signing-v2"

    fun getHardwareSecurityTier(): HardwareSecurityTier {
        return try {
            val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = getOrCreateBiometricKey()
            val factory = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityTier.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityTier.TRUSTED_ENVIRONMENT
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> HardwareSecurityTier.SOFTWARE
                    else -> HardwareSecurityTier.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) HardwareSecurityTier.TRUSTED_ENVIRONMENT
                else HardwareSecurityTier.SOFTWARE
            }
        } catch (_: Exception) {
            HardwareSecurityTier.UNKNOWN
        }
    }

    fun freshBiometricSignature(): Signature {
        val privateKey = getOrCreateBiometricKey()
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
        }
    }

    private fun getOrCreateBiometricKey(): PrivateKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(BIOMETRIC_SIGNING_ALIAS)) {
            val key = store.getKey(BIOMETRIC_SIGNING_ALIAS, null) as? PrivateKey
            if (key != null) return key
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            BIOMETRIC_SIGNING_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)

        generator.initialize(specBuilder.build())
        generator.generateKeyPair()

        return (store.getKey(BIOMETRIC_SIGNING_ALIAS, null) as PrivateKey)
    }
}
