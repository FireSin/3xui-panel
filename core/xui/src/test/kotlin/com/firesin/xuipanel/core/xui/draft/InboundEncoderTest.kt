package com.firesin.xuipanel.core.xui.draft

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundEncoderTest {

    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun `vless reality tcp encodes all three blobs with expected shape`() {
        val draft = InboundDraft(
            remark = "VLESS-443",
            port = 443,
            protocol = ProtocolSettings.Vless(
                clients = listOf(
                    VlessClient(
                        id = "uuid-1",
                        email = "user1",
                        flow = "xtls-rprx-vision",
                        totalGB = 0,
                    ),
                ),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Tcp(),
                security = SecurityConfig.Reality(
                    dest = "yahoo.com:443",
                    serverNames = listOf("yahoo.com"),
                    privateKey = "priv",
                    publicKey = "pub",
                    shortIds = listOf("aabb"),
                ),
            ),
        )

        val req = InboundEncoder.encode(draft)

        assertEquals("vless", req.protocol)
        assertEquals(443, req.port)
        assertEquals("VLESS-443", req.remark)

        val settings = parser.parseToJsonElement(req.settings).jsonObject
        assertEquals("none", settings["decryption"]!!.jsonPrimitive.content)
        val clients = settings["clients"]!!.jsonArray
        assertEquals(1, clients.size)
        assertEquals("uuid-1", clients[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", clients[0].jsonObject["flow"]!!.jsonPrimitive.content)

        val stream = parser.parseToJsonElement(req.streamSettings).jsonObject
        assertEquals("tcp", stream["network"]!!.jsonPrimitive.content)
        assertEquals("reality", stream["security"]!!.jsonPrimitive.content)
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals("yahoo.com:443", reality["dest"]!!.jsonPrimitive.content)
        assertEquals("yahoo.com", reality["serverNames"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("priv", reality["privateKey"]!!.jsonPrimitive.content)
        assertEquals("pub", reality["settings"]!!.jsonObject["publicKey"]!!.jsonPrimitive.content)
        assertEquals("aabb", reality["shortIds"]!!.jsonArray[0].jsonPrimitive.content)

        val sniffing = parser.parseToJsonElement(req.sniffing).jsonObject
        assertTrue(sniffing["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(3, (sniffing["destOverride"] as JsonArray).size)
    }

    @Test
    fun `vmess ws tls encodes wsSettings and tlsSettings`() {
        val draft = InboundDraft(
            remark = "VMess-WS",
            port = 8443,
            protocol = ProtocolSettings.Vmess(
                clients = listOf(VmessClient(id = "vmess-uuid", email = "v1")),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Ws(path = "/ray", host = "cdn.example.com"),
                security = SecurityConfig.Tls(
                    serverName = "cdn.example.com",
                    alpn = listOf("h2", "http/1.1"),
                ),
            ),
        )

        val req = InboundEncoder.encode(draft)
        val stream = parser.parseToJsonElement(req.streamSettings).jsonObject
        assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
        assertEquals("/ray", stream["wsSettings"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        assertEquals(
            "cdn.example.com",
            stream["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content,
        )
        val alpn = stream["tlsSettings"]!!.jsonObject["alpn"]!!.jsonArray
        assertEquals(2, alpn.size)
        assertEquals("h2", alpn[0].jsonPrimitive.content)
    }

    @Test
    fun `trojan grpc with fallback emits fallbacks list`() {
        val draft = InboundDraft(
            remark = "Trojan-GRPC",
            port = 443,
            protocol = ProtocolSettings.Trojan(
                clients = listOf(TrojanClient(password = "p1", email = "t1")),
                fallbacks = listOf(Fallback(dest = "80")),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Grpc(serviceName = "TunnelService"),
                security = SecurityConfig.None,
            ),
        )

        val req = InboundEncoder.encode(draft)
        val settings = parser.parseToJsonElement(req.settings).jsonObject
        val fallbacks = settings["fallbacks"]!!.jsonArray
        assertEquals(1, fallbacks.size)
        assertEquals("80", fallbacks[0].jsonObject["dest"]!!.jsonPrimitive.content)

        val stream = parser.parseToJsonElement(req.streamSettings).jsonObject
        assertEquals("grpc", stream["network"]!!.jsonPrimitive.content)
        assertEquals("none", stream["security"]!!.jsonPrimitive.content)
        assertEquals(
            "TunnelService",
            stream["grpcSettings"]!!.jsonObject["serviceName"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `shadowsocks emits inbound-level method password and clients`() {
        val draft = InboundDraft(
            remark = "SS",
            port = 8388,
            protocol = ProtocolSettings.Shadowsocks(
                method = "2022-blake3-aes-128-gcm",
                password = "inb-pass",
                clients = listOf(ShadowsocksClient(password = "c-pass", email = "ss1")),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Tcp(),
                security = SecurityConfig.None,
            ),
        )

        val req = InboundEncoder.encode(draft)
        assertEquals("shadowsocks", req.protocol)
        val s = parser.parseToJsonElement(req.settings).jsonObject
        assertEquals("2022-blake3-aes-128-gcm", s["method"]!!.jsonPrimitive.content)
        assertEquals("inb-pass", s["password"]!!.jsonPrimitive.content)
        assertEquals("tcp,udp", s["network"]!!.jsonPrimitive.content)
        assertEquals(1, s["clients"]!!.jsonArray.size)
    }

    @Test
    fun `socks with accounts emits auth and accounts array`() {
        val draft = InboundDraft(
            remark = "Socks",
            port = 1080,
            protocol = ProtocolSettings.Socks(
                accounts = listOf(UserPass(user = "u1", pass = "p1")),
                udp = true,
            ),
            stream = null,
        )

        val req = InboundEncoder.encode(draft)
        val s = parser.parseToJsonElement(req.settings).jsonObject
        assertEquals("password", s["auth"]!!.jsonPrimitive.content)
        assertEquals("u1", s["accounts"]!!.jsonArray[0].jsonObject["user"]!!.jsonPrimitive.content)
        assertEquals("{}", req.streamSettings)
    }

    @Test
    fun `disabled sniffing emits enabled false`() {
        val draft = InboundDraft(
            remark = "x",
            port = 1,
            protocol = ProtocolSettings.Http(),
            sniffing = SniffingConfig.Disabled,
        )
        val req = InboundEncoder.encode(draft)
        val s = parser.parseToJsonElement(req.sniffing).jsonObject
        assertFalse(s["enabled"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `tcp http header obfuscation emits header request with path and host`() {
        val draft = InboundDraft(
            remark = "VLESS-HTTP-obfs",
            port = 80,
            protocol = ProtocolSettings.Vless(
                clients = listOf(VlessClient(id = "u", email = "e")),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Tcp(
                    header = TcpHeader.Http(path = "/api", host = "example.com"),
                ),
                security = SecurityConfig.None,
            ),
        )
        val req = InboundEncoder.encode(draft)
        val tcp = parser.parseToJsonElement(req.streamSettings).jsonObject["tcpSettings"]!!.jsonObject
        val header = tcp["header"]!!.jsonObject
        assertEquals("http", header["type"]!!.jsonPrimitive.content)
        val request = header["request"]!!.jsonObject
        assertEquals("/api", request["path"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(
            "example.com",
            request["headers"]!!.jsonObject["Host"]!!.jsonArray[0].jsonPrimitive.content,
        )
    }
}
