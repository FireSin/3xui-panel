package com.firesin.xuipanel.core.network.tls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

class SpkiHasherTest {

    @Test
    fun `sha256Base64 produces correct base64 for known input`() {
        // Known vector: SHA-256 of the byte sequence [0x00] = 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d
        val input = byteArrayOf(0x00)
        val result = SpkiHasher.sha256Base64(input)
        // Compute expected manually
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input)
        val expected = Base64.getEncoder().encodeToString(digest)
        assertEquals(expected, result)
    }

    @Test
    fun `sha256Base64 of empty bytes is stable`() {
        val input = ByteArray(0)
        val result1 = SpkiHasher.sha256Base64(input)
        val result2 = SpkiHasher.sha256Base64(input)
        assertEquals(result1, result2)
    }

    @Test
    fun `sha256Base64 different inputs produce different hashes`() {
        val a = SpkiHasher.sha256Base64(byteArrayOf(0x01))
        val b = SpkiHasher.sha256Base64(byteArrayOf(0x02))
        assert(a != b)
    }
}
