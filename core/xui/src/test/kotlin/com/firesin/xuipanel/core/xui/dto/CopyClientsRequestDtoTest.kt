package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopyClientsRequestDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialises all fields including empty lists`() {
        val dto = CopyClientsRequestDto(
            targetInboundId = 3,
            sourceInboundId = 7,
            clientEmails = emptyList(),
            flow = "",
        )
        val encoded = json.encodeToString(CopyClientsRequestDto.serializer(), dto)
        assertEquals("""{"id":3,"sourceInboundId":7,"clientEmails":[],"flow":""}""", encoded)
    }

    @Test
    fun `serialises with selected emails and flow`() {
        val dto = CopyClientsRequestDto(
            targetInboundId = 1,
            sourceInboundId = 2,
            clientEmails = listOf("alice", "bob"),
            flow = "xtls-rprx-vision",
        )
        val encoded = json.encodeToString(CopyClientsRequestDto.serializer(), dto)
        val decoded = json.decodeFromString(CopyClientsRequestDto.serializer(), encoded)

        assertEquals(1, decoded.targetInboundId)
        assertEquals(2, decoded.sourceInboundId)
        assertEquals(listOf("alice", "bob"), decoded.clientEmails)
        assertEquals("xtls-rprx-vision", decoded.flow)
    }

    @Test
    fun `roundtrip preserves targetInboundId in id field`() {
        val raw = """{"id":5,"sourceInboundId":9,"clientEmails":["user@test"],"flow":""}"""
        val decoded = json.decodeFromString(CopyClientsRequestDto.serializer(), raw)
        assertEquals(5, decoded.targetInboundId)
        assertEquals(9, decoded.sourceInboundId)
        assertEquals(listOf("user@test"), decoded.clientEmails)
    }
}
