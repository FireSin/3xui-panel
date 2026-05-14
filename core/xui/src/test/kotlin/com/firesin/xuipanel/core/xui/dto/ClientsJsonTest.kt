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
    fun `parse_trojan_clients parses password and flow`() {
        val settings = """
            {
              "clients": [
                {
                  "password": "trojanpass123",
                  "flow": "xtls-rprx-vision",
                  "email": "trojan-user",
                  "limitIp": 2,
                  "totalGB": 5368709120,
                  "expiryTime": 1780000000000,
                  "enable": true,
                  "tgId": "",
                  "subId": "trojansubid12345",
                  "comment": "trojan test",
                  "reset": 0
                }
              ]
            }
        """.trimIndent()

        val clients = ClientsJson.parse("trojan", settings)

        assertEquals(1, clients.size)
        val client = clients[0] as ClientConfig.Trojan
        assertEquals("trojanpass123", client.password)
        assertEquals("xtls-rprx-vision", client.flow)
        assertEquals("trojan-user", client.email)
        assertEquals(2, client.limitIp)
        assertEquals(5368709120L, client.totalGB)
        assertEquals(1780000000000L, client.expiryTime)
        assertTrue(client.enable)
        assertEquals("trojansubid12345", client.subId)
        assertEquals("trojan test", client.comment)
    }

    @Test
    fun `parse_hysteria_clients parses auth field`() {
        val settings = """
            {
              "clients": [
                {
                  "auth": "hysteriaauth456",
                  "email": "hysteria-user",
                  "limitIp": 0,
                  "totalGB": 0,
                  "expiryTime": 0,
                  "enable": true,
                  "tgId": "tg123",
                  "subId": "hysteriasubid123",
                  "comment": "",
                  "reset": 0
                }
              ]
            }
        """.trimIndent()

        val clients = ClientsJson.parse("hysteria", settings)

        assertEquals(1, clients.size)
        val client = clients[0] as ClientConfig.Hysteria
        assertEquals("hysteriaauth456", client.auth)
        assertEquals("hysteria-user", client.email)
        assertEquals(0, client.limitIp)
        assertEquals(0L, client.totalGB)
        assertEquals(0L, client.expiryTime)
        assertTrue(client.enable)
        assertEquals("tg123", client.tgId)
        assertEquals("hysteriasubid123", client.subId)
    }

    @Test
    fun `encode_trojan_client_round_trip`() {
        val original = ClientConfig.Trojan(
            password = "trojanpass123",
            flow = "xtls-rprx-vision",
            email = "trojan-user",
            enable = true,
            totalGB = 5368709120L,
            expiryTime = 1780000000000L,
            limitIp = 2,
            subId = "trojansubid12345",
            comment = "trojan test",
            tgId = "",
            reset = 0,
            createdAt = null,
            updatedAt = null,
        )

        val encoded = ClientsJson.encodeSettingsBody(original)
        val parsed = ClientsJson.parse("trojan", encoded)

        assertEquals(1, parsed.size)
        assertEquals(original, parsed[0])
    }

    @Test
    fun `encode_hysteria_client_round_trip`() {
        val original = ClientConfig.Hysteria(
            auth = "hysteriaauth456",
            email = "hysteria-user",
            enable = false,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "hysteriasubid123",
            comment = "",
            tgId = "tg123",
            reset = 0,
            createdAt = 1777534536000L,
            updatedAt = 1777534600000L,
        )

        val encoded = ClientsJson.encodeSettingsBody(original)
        val parsed = ClientsJson.parse("hysteria", encoded)

        assertEquals(1, parsed.size)
        assertEquals(original, parsed[0])
    }

    @Test
    fun `trojan flow defaults to empty string when absent`() {
        val settings = """
            {
              "clients": [
                {
                  "password": "pass",
                  "email": "user",
                  "limitIp": 0,
                  "totalGB": 0,
                  "expiryTime": 0,
                  "enable": true,
                  "tgId": "",
                  "subId": "",
                  "comment": "",
                  "reset": 0
                }
              ]
            }
        """.trimIndent()

        val client = ClientsJson.parse("trojan", settings)[0] as ClientConfig.Trojan
        assertEquals("", client.flow)
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
