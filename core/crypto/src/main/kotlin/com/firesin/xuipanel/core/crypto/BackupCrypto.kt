package com.firesin.xuipanel.core.crypto

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedBundle(
    val salt: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val kdfIterations: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBundle) return false
        return kdfIterations == other.kdfIterations &&
            salt.contentEquals(other.salt) &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + kdfIterations
        return result
    }
}

sealed class BackupError {
    data object WrongPassphrase : BackupError()
    data object MalformedBundle : BackupError()
    data class Unexpected(val cause: Throwable) : BackupError()
}

object BackupCrypto {

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val NONCE_LENGTH_BYTES = 12
    private const val KDF_ITERATIONS = 600_000

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): EncryptedBundle {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { random.nextBytes(it) }

        val key = deriveKey(passphrase, salt, KDF_ITERATIONS)
        Arrays.fill(passphrase, ' ')

        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedBundle(salt = salt, nonce = nonce, ciphertext = ciphertext, kdfIterations = KDF_ITERATIONS)
        } finally {
            Arrays.fill(key.encoded, 0)
        }
    }

    fun decrypt(bundle: EncryptedBundle, passphrase: CharArray): com.firesin.xuipanel.core.common.Result<ByteArray, BackupError> {
        val key = deriveKey(passphrase, bundle.salt, bundle.kdfIterations)
        Arrays.fill(passphrase, ' ')

        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, bundle.nonce))
            val plaintext = cipher.doFinal(bundle.ciphertext)
            com.firesin.xuipanel.core.common.Result.Success(plaintext)
        } catch (e: AEADBadTagException) {
            com.firesin.xuipanel.core.common.Result.Failure(BackupError.WrongPassphrase)
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            com.firesin.xuipanel.core.common.Result.Failure(BackupError.MalformedBundle)
        } catch (e: java.security.GeneralSecurityException) {
            com.firesin.xuipanel.core.common.Result.Failure(BackupError.MalformedBundle)
        } catch (e: Exception) {
            com.firesin.xuipanel.core.common.Result.Failure(BackupError.Unexpected(e))
        } finally {
            Arrays.fill(key.encoded, 0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val rawKey = factory.generateSecret(spec).encoded
            SecretKeySpec(rawKey, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
