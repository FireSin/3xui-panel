package com.firesin.xuipanel.core.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrettyBytesTest {

    private fun check(bytes: Long, expected: String) =
        assertEquals(expected, prettyBytes(bytes), "prettyBytes($bytes)")

    @Test
    fun `zero returns 0 B`() = check(0L, "0 B")

    @Test
    fun `negative returns 0 B`() = check(-1L, "0 B")

    @Test
    fun `large negative returns 0 B`() = check(-1_048_576L, "0 B")

    @Test
    fun `1 byte`() = check(1L, "1 B")

    @Test
    fun `1023 bytes stays in B`() = check(1023L, "1023 B")

    @Test
    fun `1024 bytes is 1_0 KB`() = check(1024L, "1.0 KB")

    @Test
    fun `1536 bytes is 1_5 KB`() = check(1536L, "1.5 KB")

    @Test
    fun `1500 bytes is 1_5 KB`() = check(1500L, "1.5 KB")

    @Test
    fun `1023 times 1024 is 1023_0 KB`() = check(1023L * 1024L, "1023.0 KB")

    @Test
    fun `1 MB boundary`() = check(1_048_576L, "1.0 MB")

    @Test
    fun `1_5 MB`() = check(1_572_864L, "1.5 MB")

    @Test
    fun `1 GB boundary`() = check(1_073_741_824L, "1.0 GB")

    @Test
    fun `1_5 GB`() = check(1_610_612_736L, "1.5 GB")

    @Test
    fun `1 TB boundary`() = check(1_099_511_627_776L, "1.0 TB")

    @Test
    fun `2 TB`() = check(2_199_023_255_552L, "2.0 TB")
}
