package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val json = Json { ignoreUnknownKeys = true }

private fun loadFixture(name: String): String =
    checkNotNull(ClientsJsonTest::class.java.classLoader?.getResourceAsStream("clients/$name")) {
        "Missing fixture: clients/$name"
    }.bufferedReader().readText()

private fun jsonEquals(a: String, b: String): Boolean =
    json.parseToJsonElement(a) == json.parseToJsonElement(b)

class ClientsJsonTest {

    @Test
    fun `vless add fixture round-trips correctly`() {
        val fixture = loadFixture("vless_add_settings.json")

        val clients = ClientsJson.parse("vless", fixture)

        assertEquals(1, clients.size)
        val client = clients[0] as ClientConfig.Vless
        assertEquals("e927a2d3-0bbd-4f8b-9493-b65eab8bfbe3", client.id)
        assertEquals("xtls-rprx-vision", client.flow)
        assertEquals("testAdd", client.email)
        assertEquals(1, client.limitIp)
        assertEquals(0L, client.totalGB)
        assertEquals(1780126511926L, client.expiryTime)
        assertTrue(client.enable)
        assertEquals("", client.tgId)
        assertEquals("testAddSub", client.subId)
        assertEquals("", client.comment)
        assertEquals(0, client.reset)
        assertNull(client.createdAt)
        assertNull(client.updatedAt)

        val encoded = ClientsJson.encodeSettingsBody(client)
        assertTrue(jsonEquals(fixture, encoded), "Encoded JSON should equal fixture (order-insensitive)")
    }

    @Test
    fun `vless update fixture round-trips with created_at and updated_at`() {
        val fixture = loadFixture("vless_update_settings.json")

        val clients = ClientsJson.parse("vless", fixture)

        assertEquals(1, clients.size)
        val client = clients[0] as ClientConfig.Vless
        assertEquals(1777534536000L, client.createdAt)
        assertEquals(1777534536000L, client.updatedAt)
        assertEquals("testAdd1", client.email)

        val encoded = ClientsJson.encodeSettingsBody(client)
        assertTrue(jsonEquals(fixture, encoded), "Encoded JSON should equal update fixture (order-insensitive)")
    }

    @Test
    fun `vmess synthetic round-trip`() {
        val original = ClientConfig.Vmess(
            id = "11111111-2222-3333-4444-555555555555",
            email = "vmess-user",
            enable = true,
            totalGB = 10737418240L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "abcdef1234567890",
            comment = "test comment",
            tgId = "",
            reset = 0,
            createdAt = null,
            updatedAt = null,
        )

        val encoded = ClientsJson.encodeSettingsBody(original)
        val parsed = ClientsJson.parse("vmess", encoded)

        assertEquals(1, parsed.size)
        assertEquals(original, parsed[0])
    }

    @Test
    fun `shadowsocks synthetic round-trip`() {
        val original = ClientConfig.Shadowsocks(
            password = "dGVzdHBhc3N3b3JkMTIzNDU2Nzg5MDEyMzQ=",
            method = "chacha20-ietf-poly1305",
            email = "ss-user",
            enable = false,
            totalGB = 0L,
            expiryTime = 1780000000000L,
            limitIp = 2,
            subId = "sssubid12345678",
            comment = "",
            tgId = "123456789",
            reset = 0,
            createdAt = 1777534536000L,
            updatedAt = 1777534600000L,
        )

        val encoded = ClientsJson.encodeSettingsBody(original)
        val parsed = ClientsJson.parse("shadowsocks", encoded)

        assertEquals(1, parsed.size)
        assertEquals(original, parsed[0])
    }

    @Test
    fun `extra unknown top-level keys do not throw`() {
        val settingsWithExtras = """
            {
              "clients": [
                {
                  "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                  "flow": "",
                  "email": "user",
                  "limitIp": 0,
                  "totalGB": 0,
                  "expiryTime": 0,
                  "enable": true,
                  "tgId": "",
                  "subId": "sub1",
                  "comment": "",
                  "reset": 0
                }
              ],
              "decryption": "none",
              "fallbacks": []
            }
        """.trimIndent()

        val clients = ClientsJson.parse("vless", settingsWithExtras)
        assertEquals(1, clients.size)
    }

    @Test
    fun `empty clients array returns empty list`() {
        val settings = """{"clients":[],"decryption":"none"}"""
        val clients = ClientsJson.parse("vless", settings)
        assertTrue(clients.isEmpty())
    }

    @Test
    fun `missing created_at on parse yields null`() {
        val settings = """
            {
              "clients": [
                {
                  "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                  "flow": "xtls-rprx-vision",
                  "email": "user",
                  "limitIp": 0,
                  "totalGB": 0,
                  "expiryTime": 0,
                  "enable": true,
                  "tgId": "",
                  "subId": "sub1",
                  "comment": "",
                  "reset": 0
                }
              ]
            }
        """.trimIndent()

        val client = ClientsJson.parse("vless", settings)[0] as ClientConfig.Vless
        assertNull(client.createdAt)
        assertNull(client.updatedAt)
    }
}
