package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientTrafficDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `parses happy-path response correctly`() {
        val raw = """
            {
              "success": true,
              "obj": {
                "id": 1,
                "inboundId": 5,
                "enable": true,
                "email": "user@example.com",
                "up": 12345,
                "down": 67890,
                "expiryTime": 0,
                "total": 0,
                "reset": 3
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<ClientTrafficResponseDto>(raw)

        assertTrue(dto.success)
        val obj = dto.obj!!
        assertEquals(1, obj.id)
        assertEquals(5, obj.inboundId)
        assertTrue(obj.enable)
        assertEquals("user@example.com", obj.email)
        assertEquals(12345L, obj.up)
        assertEquals(67890L, obj.down)
        assertEquals(0L, obj.expiryTime)
        assertEquals(0L, obj.total)
        assertEquals(3L, obj.reset)
    }

    @Test
    fun `parses response with non-zero expiryTime and total`() {
        val raw = """
            {
              "success": true,
              "obj": {
                "id": 2,
                "inboundId": 1,
                "enable": true,
                "email": "alice@test.com",
                "up": 1073741824,
                "down": 5368709120,
                "expiryTime": 1735689600000,
                "total": 10737418240,
                "reset": 0
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<ClientTrafficResponseDto>(raw)

        assertTrue(dto.success)
        val obj = dto.obj!!
        assertEquals(1_073_741_824L, obj.up)
        assertEquals(5_368_709_120L, obj.down)
        assertEquals(1_735_689_600_000L, obj.expiryTime)
        assertEquals(10_737_418_240L, obj.total)
    }

    @Test
    fun `parses failure response without obj`() {
        val raw = """{"success": false, "msg": "email not found"}"""

        val dto = json.decodeFromString<ClientTrafficResponseDto>(raw)

        assertTrue(!dto.success)
        assertNull(dto.obj)
        assertEquals("email not found", dto.msg)
    }

    @Test
    fun `ignores unknown keys`() {
        val raw = """
            {
              "success": true,
              "obj": {
                "id": 3,
                "inboundId": 2,
                "enable": false,
                "email": "bob@test.com",
                "up": 0,
                "down": 0,
                "expiryTime": 0,
                "total": 0,
                "reset": 0,
                "unknownFutureField": "ignored"
              },
              "extraTopLevel": 42
            }
        """.trimIndent()

        val dto = json.decodeFromString<ClientTrafficResponseDto>(raw)

        assertTrue(dto.success)
        assertEquals("bob@test.com", dto.obj?.email)
    }
}
