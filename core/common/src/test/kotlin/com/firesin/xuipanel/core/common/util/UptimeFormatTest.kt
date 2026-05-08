package com.firesin.xuipanel.core.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UptimeFormatTest {

    @Test
    fun `zero returns 0m`() {
        assertEquals("0m", secondsToCompact(0L))
    }

    @Test
    fun `negative returns 0m`() {
        assertEquals("0m", secondsToCompact(-100L))
    }

    @Test
    fun `under one hour shows minutes only`() {
        assertEquals("45m", secondsToCompact(45 * 60L))
    }

    @Test
    fun `exactly one hour`() {
        assertEquals("1h 0m", secondsToCompact(3_600L))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals("6h 30m", secondsToCompact(6 * 3_600L + 30 * 60L))
    }

    @Test
    fun `exactly one day`() {
        assertEquals("1d 0h", secondsToCompact(86_400L))
    }

    @Test
    fun `days and hours`() {
        assertEquals("14d 6h", secondsToCompact(14 * 86_400L + 6 * 3_600L))
    }

    @Test
    fun `days minutes ignored`() {
        // "14d 06h 30m" → format only shows days+hours
        assertEquals("14d 6h", secondsToCompact(14 * 86_400L + 6 * 3_600L + 30 * 60L))
    }
}
