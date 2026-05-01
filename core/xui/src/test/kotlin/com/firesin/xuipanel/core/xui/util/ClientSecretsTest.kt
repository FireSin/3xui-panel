package com.firesin.xuipanel.core.xui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ClientSecretsTest {

    @Test
    fun `randomUuid returns valid UUID format`() {
        val uuid = randomUuid()
        // Validates that UUID.fromString does not throw
        val parsed = UUID.fromString(uuid)
        assertEquals(uuid, parsed.toString())
    }

    @Test
    fun `randomShadowsocksPassword is non-empty and has stable length`() {
        val pass1 = randomShadowsocksPassword()
        val pass2 = randomShadowsocksPassword()
        assertTrue(pass1.isNotEmpty())
        // 32 bytes base64-encoded with padding = ceil(32/3)*4 = 44 chars
        assertEquals(44, pass1.length)
        assertEquals(44, pass2.length)
    }

    @Test
    fun `randomSubId is 16 characters from a-z0-9`() {
        val subId = randomSubId()
        assertEquals(16, subId.length)
        assertTrue(subId.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun `randomSubId is different across calls`() {
        val ids = (1..10).map { randomSubId() }.toSet()
        // With 36^16 combinations, collision probability is negligible
        assertTrue(ids.size > 1)
    }
}
