package com.firesin.xuipanel.core.designsystem.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BytesTest {

    @Test
    fun `zero bytes returns zero B`() {
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `one byte returns one B`() {
        assertEquals("1 B", formatBytes(1))
    }

    @Test
    fun `1023 bytes returns 1023 B`() {
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `1024 bytes returns one KB`() {
        val result = formatBytes(1024)
        assertEquals("1.00 KB", result)
    }

    @Test
    fun `1 MB minus 1 byte formats correctly`() {
        val result = formatBytes(1024 * 1024 - 1)
        assertEquals("1024.00 KB", result)
    }

    @Test
    fun `exactly 1 MB returns one MB`() {
        val result = formatBytes(1024 * 1024)
        assertEquals("1.00 MB", result)
    }

    @Test
    fun `1.5 GB formats correctly`() {
        val bytes = (1.5 * 1024 * 1024 * 1024).toLong()
        val result = formatBytes(bytes)
        assertEquals("1.50 GB", result)
    }

    @Test
    fun `very large value in PB range`() {
        val bytes = (2.5 * 1024L * 1024L * 1024L * 1024L * 1024L).toLong()
        val result = formatBytes(bytes)
        assertEquals("2.50 PB", result)
    }

    @Test
    fun `512 KB`() {
        val result = formatBytes(512 * 1024)
        assertEquals("512.00 KB", result)
    }

    @Test
    fun `10 GB`() {
        val result = formatBytes((10 * 1024 * 1024 * 1024).toLong())
        assertEquals("10.00 GB", result)
    }
}
