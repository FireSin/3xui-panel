package com.firesin.xuipanel.core.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpiryTest {

    private val now = 1_700_000_000_000L // fixed epoch for determinism

    @Test
    fun `zero expiryMs returns Never`() {
        assertEquals(ExpiryLabel.Never, classifyExpiry(0L, now))
    }

    @Test
    fun `negative expiryMs returns Never`() {
        assertEquals(ExpiryLabel.Never, classifyExpiry(-1L, now))
    }

    @Test
    fun `1 hour in future returns ExpiresIn 0 days`() {
        val expiryMs = now + 3_600_000L // +1 h
        assertEquals(ExpiryLabel.ExpiresIn(0), classifyExpiry(expiryMs, now))
    }

    @Test
    fun `exactly 1 day in future returns ExpiresIn 1 day`() {
        val expiryMs = now + 86_400_000L
        assertEquals(ExpiryLabel.ExpiresIn(1), classifyExpiry(expiryMs, now))
    }

    @Test
    fun `30 days in future returns ExpiresIn 30 days`() {
        val expiryMs = now + 30L * 86_400_000L
        assertEquals(ExpiryLabel.ExpiresIn(30), classifyExpiry(expiryMs, now))
    }

    @Test
    fun `1 hour in past returns ExpiredAgo 0 days`() {
        val expiryMs = now - 3_600_000L // -1 h
        assertEquals(ExpiryLabel.ExpiredAgo(0), classifyExpiry(expiryMs, now))
    }

    @Test
    fun `exactly 1 day in past returns ExpiredAgo 1 day`() {
        val expiryMs = now - 86_400_000L
        assertEquals(ExpiryLabel.ExpiredAgo(1), classifyExpiry(expiryMs, now))
    }

    @Test
    fun `30 days in past returns ExpiredAgo 30 days`() {
        val expiryMs = now - 30L * 86_400_000L
        assertEquals(ExpiryLabel.ExpiredAgo(30), classifyExpiry(expiryMs, now))
    }
}
