package com.firesin.xuipanel.core.crypto

import com.firesin.xuipanel.core.common.Result
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCryptoTest {

    @Test
    fun `round-trip returns original plaintext`() {
        val plaintext = "hello backup".toByteArray()
        val bundle = BackupCrypto.encrypt(plaintext, "strongpass1".toCharArray())
        val result = BackupCrypto.decrypt(bundle, "strongpass1".toCharArray())
        assertTrue(result is Result.Success)
        assertArrayEquals(plaintext, (result as Result.Success).data)
    }

    @Test
    fun `wrong password returns WrongPassphrase`() {
        val bundle = BackupCrypto.encrypt("secret".toByteArray(), "correct-pass".toCharArray())
        val result = BackupCrypto.decrypt(bundle, "wrong-pass".toCharArray())
        assertEquals(Result.Failure(BackupError.WrongPassphrase), result)
    }

    @Test
    fun `tampered ciphertext returns WrongPassphrase`() {
        val bundle = BackupCrypto.encrypt("data".toByteArray(), "pass12345".toCharArray())
        val tampered = bundle.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        val corruptBundle = bundle.copy(ciphertext = tampered)
        val result = BackupCrypto.decrypt(corruptBundle, "pass12345".toCharArray())
        assertEquals(Result.Failure(BackupError.WrongPassphrase), result)
    }

    @Test
    fun `round-trip with empty plaintext`() {
        val plaintext = ByteArray(0)
        val bundle = BackupCrypto.encrypt(plaintext, "emptytest".toCharArray())
        val result = BackupCrypto.decrypt(bundle, "emptytest".toCharArray())
        assertTrue(result is Result.Success)
        assertArrayEquals(plaintext, (result as Result.Success).data)
    }

    @Test
    fun `round-trip with 1MB plaintext`() {
        val plaintext = ByteArray(1 * 1024 * 1024) { it.toByte() }
        val bundle = BackupCrypto.encrypt(plaintext, "largefilepass".toCharArray())
        val result = BackupCrypto.decrypt(bundle, "largefilepass".toCharArray())
        assertTrue(result is Result.Success)
        assertArrayEquals(plaintext, (result as Result.Success).data)
    }
}
