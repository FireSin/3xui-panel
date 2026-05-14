package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddInboundRequestDtoTest {

    /** Matches the Json instance used by XuiClient (ignoreUnknownKeys + explicitNulls default = true). */
    private val json = Json { ignoreUnknownKeys = true }

    private fun minimalDto(nodeId: Int? = null) = AddInboundRequestDto(
        remark = "test",
        port = 443,
        protocol = "vless",
        settings = "{}",
        streamSettings = "{}",
        sniffing = "{}",
        nodeId = nodeId,
    )

    @Test
    fun `nodeId null is absent from serialized JSON`() {
        val dto = minimalDto(nodeId = null)
        val encoded = json.encodeToString(AddInboundRequestDto.serializer(), dto)
        assertFalse(encoded.contains("nodeId"), "nodeId must be absent when null, but was: $encoded")
    }

    @Test
    fun `nodeId non-null is present in serialized JSON`() {
        val dto = minimalDto(nodeId = 1)
        val encoded = json.encodeToString(AddInboundRequestDto.serializer(), dto)
        assertTrue(encoded.contains("\"nodeId\":1"), "nodeId must be present when non-null, but was: $encoded")
    }
}
