package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val json = Json { ignoreUnknownKeys = true }

class OnlinesResponseDtoTest {

    @Test
    fun `happy path - success true with email list`() {
        val dto = json.decodeFromString<OnlinesResponseDto>(
            """{"success":true,"obj":["a@b","c@d"]}"""
        )
        assertTrue(dto.success)
        assertEquals(listOf("a@b", "c@d"), dto.obj)
        assertNull(dto.msg)
    }

    @Test
    fun `success true with null obj field absent`() {
        val dto = json.decodeFromString<OnlinesResponseDto>(
            """{"success":true}"""
        )
        assertTrue(dto.success)
        assertNull(dto.obj)
    }

    @Test
    fun `success false with msg`() {
        val dto = json.decodeFromString<OnlinesResponseDto>(
            """{"success":false,"msg":"err"}"""
        )
        assertFalse(dto.success)
        assertNull(dto.obj)
        assertEquals("err", dto.msg)
    }

    @Test
    fun `unknown fields are ignored`() {
        val dto = json.decodeFromString<OnlinesResponseDto>(
            """{"success":true,"obj":[],"extra":"ignored","version":42}"""
        )
        assertTrue(dto.success)
        assertEquals(emptyList<String>(), dto.obj)
    }
}
