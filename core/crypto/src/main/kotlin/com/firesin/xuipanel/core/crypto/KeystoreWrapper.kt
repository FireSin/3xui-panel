package com.firesin.xuipanel.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM wrap/unwrap via Android Keystore.
 * The key is non-exportable and created with StrongBox when available (falls back to TEE).
 *
 * Wire format for wrapped bytes: IV (12 bytes) ‖ ciphertext ‖ GCM tag (16 bytes).
 */
class KeystoreWrapper {

    private val keyAlias = KEY_ALIAS

    fun wrap(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv          // 12 bytes for GCM
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext      // IV ‖ ciphertext ‖ tag (tag is appended by doFinal)
    }

    fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_LENGTH) { "Wrapped data is too short" }
        val iv = wrapped.copyOfRange(0, IV_LENGTH)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH, wrapped.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        return try {
            kg.init(buildKeySpec(strongBox = true))
            kg.generateKey()
        } catch (_: android.security.keystore.StrongBoxUnavailableException) {
            kg.init(buildKeySpec(strongBox = false))
            kg.generateKey()
        }
    }

    private fun buildKeySpec(strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(strongBox)
            .build()

    private companion object {
        const val KEY_ALIAS = "db_passphrase_wrap_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
