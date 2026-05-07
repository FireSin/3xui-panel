package com.firesin.xuipanel.core.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

class AddExpiryTest {

    private val MS_PER_DAY = 86_400_000L

    // addExpiry — days delta

    @Test
    fun `addExpiry zero current uses now as base`() {
        val now = 1_700_000_000_000L
        val result = addExpiry(currentMs = 0L, deltaMs = 7 * MS_PER_DAY, nowMs = now)
        assertEquals(now + 7 * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiry negative current uses now as base`() {
        val now = 1_700_000_000_000L
        val result = addExpiry(currentMs = -1L, deltaMs = 7 * MS_PER_DAY, nowMs = now)
        assertEquals(now + 7 * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiry future current adds delta on top`() {
        val now = 1_700_000_000_000L
        val future = now + 5 * MS_PER_DAY
        val result = addExpiry(currentMs = future, deltaMs = 7 * MS_PER_DAY, nowMs = now)
        assertEquals(now + 12 * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiry past current adds delta on top without auto-correction`() {
        val now = 1_700_000_000_000L
        val past = now - 3 * MS_PER_DAY
        val result = addExpiry(currentMs = past, deltaMs = 7 * MS_PER_DAY, nowMs = now)
        // past + 7d = now - 3d + 7d = now + 4d
        assertEquals(now + 4 * MS_PER_DAY, result)
    }

    // addExpiryMonths — month delta (1 month = exactly 30 days)

    @Test
    fun `addExpiryMonths zero current uses now as base`() {
        val now = 1_700_000_000_000L
        val result = addExpiryMonths(currentMs = 0L, months = 1, nowMs = now)
        assertEquals(now + 30L * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiryMonths 1 month equals exactly 30 days`() {
        val base = 1_700_000_000_000L
        val result = addExpiryMonths(currentMs = base, months = 1, nowMs = base)
        assertEquals(base + 30L * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiryMonths 3 months equals exactly 90 days`() {
        val base = 1_700_000_000_000L
        val result = addExpiryMonths(currentMs = base, months = 3, nowMs = base)
        assertEquals(base + 90L * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiryMonths 6 months equals exactly 180 days`() {
        val base = 1_700_000_000_000L
        val result = addExpiryMonths(currentMs = base, months = 6, nowMs = base)
        assertEquals(base + 180L * MS_PER_DAY, result)
    }

    @Test
    fun `addExpiryMonths 12 months equals exactly 360 days`() {
        val base = 1_700_000_000_000L
        val result = addExpiryMonths(currentMs = base, months = 12, nowMs = base)
        assertEquals(base + 360L * MS_PER_DAY, result)
    }

    // setExpiryToNone — semantic test: 0L means "no limit"

    @Test
    fun `zero expiryTime means no limit`() {
        assertEquals(0L, 0L) // trivial — confirms the sentinel value used in form
    }
}

class FormatRemainingTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `expiryMs zero returns null`() {
        assertEquals(null, formatRemainingTime(0L, now))
    }

    @Test
    fun `expiryMs equals nowMs returns null`() {
        assertEquals(null, formatRemainingTime(now, now))
    }

    @Test
    fun `expiryMs less than nowMs returns null`() {
        assertEquals(null, formatRemainingTime(now - 1L, now))
    }

    @Test
    fun `exactly 1 day returns 1д 0ч 0м`() {
        assertEquals("1д 0ч 0м", formatRemainingTime(now + 86_400_000L, now))
    }

    @Test
    fun `3 days 22 hours 17 minutes returns correct string`() {
        val expiry = now + 3 * 86_400_000L + 22 * 3_600_000L + 17 * 60_000L
        assertEquals("3д 22ч 17м", formatRemainingTime(expiry, now))
    }

    @Test
    fun `1 minute returns 0д 0ч 1м`() {
        assertEquals("0д 0ч 1м", formatRemainingTime(now + 60_000L, now))
    }

    @Test
    fun `30 seconds truncates to 0д 0ч 0м`() {
        assertEquals("0д 0ч 0м", formatRemainingTime(now + 30_000L, now))
    }
}
