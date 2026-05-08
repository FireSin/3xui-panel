package com.firesin.xuipanel.core.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTest {

    @Test
    fun `zero returns 0_0 kBps`() {
        assertEquals("0.0" to "kB/s", formatSpeed(0L))
    }

    @Test
    fun `negative returns 0_0 kBps`() {
        assertEquals("0.0" to "kB/s", formatSpeed(-1L))
    }

    @Test
    fun `bytes below 1MB shown as kBps`() {
        // 12345 bytes/s = 12345/1024 ≈ 12.1 kB/s
        val (value, unit) = formatSpeed(12_345L)
        assertEquals("kB/s", unit)
        assertEquals("12.1", value)
    }

    @Test
    fun `bytes in MB range shown as MBps`() {
        // 1.5 * 1024^2 = 1_572_864
        val (value, unit) = formatSpeed(1_572_864L)
        assertEquals("MB/s", unit)
        assertEquals("1.5", value)
    }

    @Test
    fun `bytes in GB range shown as GBps`() {
        // 2.0 GB/s
        val (value, unit) = formatSpeed(2L * 1024 * 1024 * 1024)
        assertEquals("GB/s", unit)
        assertEquals("2.0", value)
    }

    @Test
    fun `exactly 1 KB per second`() {
        val (value, unit) = formatSpeed(1024L)
        assertEquals("kB/s", unit)
        assertEquals("1.0", value)
    }
}
