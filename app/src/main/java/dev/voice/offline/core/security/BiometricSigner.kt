package dev.voice.offline.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Authentication-per-use signing key. Hardware backing is device-dependent, not guaranteed. */
object BiometricSigner {
    private const val ALIAS = "voice-approval-signing-v1"
    fun freshSignature(): Signature {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val privateKey = store.getKey(ALIAS, null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
    }
}
