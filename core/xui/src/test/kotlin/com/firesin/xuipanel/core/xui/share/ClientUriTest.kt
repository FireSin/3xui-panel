package com.firesin.xuipanel.core.xui.share

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.Base64

/**
 * Unit tests for [ClientUri.build].
 *
 * Synthetic fixtures — no live panel required.
 * URI structure verified against upstream sub/subService.go @main
 */
class ClientUriTest {

    // ── Shared test data ──────────────────────────────────────────────────────

    private val baseInbound = InboundDto(
        id = 1,
        up = 0L,
        down = 0L,
        total = 0L,
        remark = "my-panel",
        enable = true,
        expiryTime = 0L,
        clientStats = null,
        listen = "",
        port = 443,
        protocol = "vless",
        settings = """{"clients":[],"decryption":"none"}""",
        streamSettings = "",
        tag = "inbound-443",
        sniffing = "{}",
    )

    private val vlessClient = ClientConfig.Vless(
        id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        flow = "xtls-rprx-vision",
        email = "alice",
        enable = true,
        totalGB = 0L,
        expiryTime = 0L,
        limitIp = 0,
        subId = "",
        comment = "",
    )

    // ── VLESS + Reality + TCP ─────────────────────────────────────────────────

    @Test
    fun `VLESS Reality TCP - URI contains required params and correct fragment`() {
        val streamSettings = """
        {
          "network": "tcp",
          "security": "reality",
          "tcpSettings": {"header": {"type": "none"}},
          "realitySettings": {
            "serverNames": ["example.com"],
            "shortIds": ["a1b2c3d4"],
            "settings": {
              "publicKey": "testpubkey123",
              "fingerprint": "chrome"
            }
          }
        }
        """.trimIndent()

        val inbound = baseInbound.copy(streamSettings = streamSettings)
        val result = ClientUri.build(vlessClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data

        assertTrue(uri.startsWith("vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@panel.example.com:443"),
            "URI must start with vless://uuid@host:port")

        // Parse query params
        val params = parseQueryParams(uri)
        assertEquals("tcp", params["type"])
        assertEquals("reality", params["security"])
        assertEquals("testpubkey123", params["pbk"])
        assertEquals("a1b2c3d4", params["sid"])
        assertEquals("chrome", params["fp"])
        assertEquals("example.com", params["sni"])
        assertEquals("xtls-rprx-vision", params["flow"])

        // Fragment: remark = inbound.remark + "-" + email (upstream default remarkModel "-ieo")
        val fragment = URI(uri.replace(" ", "%20")).fragment
        assertEquals("my-panel-alice", fragment)
    }

    @Test
    fun `VLESS WS TLS - URI contains type=ws, path, host, security=tls, sni`() {
        val streamSettings = """
        {
          "network": "ws",
          "security": "tls",
          "wsSettings": {
            "path": "/ws-path",
            "host": "ws.example.com"
          },
          "tlsSettings": {
            "serverName": "ws.example.com",
            "alpn": ["http/1.1"],
            "settings": {"fingerprint": "chrome"}
          }
        }
        """.trimIndent()

        val inbound = baseInbound.copy(streamSettings = streamSettings)
        // flow should be dropped for WS (upstream gates flow on TCP only)
        val result = ClientUri.build(vlessClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data
        val params = parseQueryParams(uri)

        assertEquals("ws", params["type"])
        assertEquals("/ws-path", params["path"])
        assertEquals("ws.example.com", params["host"])
        assertEquals("tls", params["security"])
        assertEquals("ws.example.com", params["sni"])
        assertEquals("chrome", params["fp"])
        // flow must NOT appear for WS transport
        assertTrue(!params.containsKey("flow"), "flow must be absent for WS transport")
    }

    // ── VMESS ─────────────────────────────────────────────────────────────────

    @Test
    fun `VMESS TLS WS - base64 decodes to JSON with all required fields`() {
        val streamSettings = """
        {
          "network": "ws",
          "security": "tls",
          "wsSettings": {
            "path": "/vmess",
            "host": "vmess.example.com"
          },
          "tlsSettings": {
            "serverName": "vmess.example.com",
            "alpn": ["h2"],
            "settings": {"fingerprint": "firefox"}
          }
        }
        """.trimIndent()

        val vmessClient = ClientConfig.Vmess(
            id = "11111111-2222-3333-4444-555555555555",
            email = "bob",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        val inbound = baseInbound.copy(
            protocol = "vmess",
            port = 8443,
            streamSettings = streamSettings,
        )

        val result = ClientUri.build(vmessClient, inbound, "vmess.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data
        assertTrue(uri.startsWith("vmess://"))

        // Decode base64 payload
        val b64 = uri.removePrefix("vmess://")
        val jsonStr = String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
        assertTrue(jsonStr.contains("\"v\":\"2\""))
        assertTrue(jsonStr.contains("\"add\":\"vmess.example.com\""))
        assertTrue(jsonStr.contains("\"id\":\"11111111-2222-3333-4444-555555555555\""))
        assertTrue(jsonStr.contains("\"aid\":\"0\""))
        assertTrue(jsonStr.contains("\"scy\":\"auto\""))
        assertTrue(jsonStr.contains("\"net\":\"ws\""))
        assertTrue(jsonStr.contains("\"tls\":\"tls\""))
        assertTrue(jsonStr.contains("\"sni\":\"vmess.example.com\""))
        assertTrue(jsonStr.contains("\"alpn\":\"h2\""))
        assertTrue(jsonStr.contains("\"fp\":\"firefox\""))
        // ps = remark
        assertTrue(jsonStr.contains("\"ps\":\"my-panel-bob\""))
    }

    // ── Shadowsocks ───────────────────────────────────────────────────────────

    @Test
    fun `SS standard cipher - userinfo is base64Std(method colon password)`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val ssClient = ClientConfig.Shadowsocks(
            password = "supersecret",
            method = "chacha20-ietf-poly1305",
            email = "carol",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        val inbound = baseInbound.copy(
            protocol = "shadowsocks",
            port = 8388,
            streamSettings = streamSettings,
        )

        val result = ClientUri.build(ssClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data
        assertTrue(uri.startsWith("ss://"))

        // userinfo is between ss:// and @
        val userinfo = uri.removePrefix("ss://").substringBefore("@")
        val decoded = String(Base64.getDecoder().decode(userinfo), Charsets.UTF_8)
        assertEquals("chacha20-ietf-poly1305:supersecret", decoded)

        assertTrue(uri.contains("@panel.example.com:8388"))
    }

    @Test
    fun `SS 2022 cipher - userinfo is base64Std(method colon inboundPw colon clientPw)`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val ssClient = ClientConfig.Shadowsocks(
            password = "clientpass",
            method = "2022-blake3-aes-256-gcm",
            email = "dave",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        val inbound = baseInbound.copy(
            protocol = "shadowsocks",
            port = 8388,
            settings = """{"method":"2022-blake3-aes-256-gcm","password":"inboundpass","clients":[]}""",
            streamSettings = streamSettings,
        )

        val result = ClientUri.build(ssClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data
        val userinfo = uri.removePrefix("ss://").substringBefore("@")
        val decoded = String(Base64.getDecoder().decode(userinfo), Charsets.UTF_8)
        assertEquals("2022-blake3-aes-256-gcm:inboundpass:clientpass", decoded)
    }

    @Test
    fun `SS 2022 cipher without inbound password returns MissingInboundPassword`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val ssClient = ClientConfig.Shadowsocks(
            password = "clientpass",
            method = "2022-blake3-aes-256-gcm",
            email = "eve",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        // inbound settings has no "password" field
        val inbound = baseInbound.copy(
            protocol = "shadowsocks",
            settings = """{"method":"2022-blake3-aes-256-gcm","clients":[]}""",
            streamSettings = streamSettings,
        )

        val result = ClientUri.build(ssClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Failure::class.java, result)
        assertInstanceOf(ShareError.MissingInboundPassword::class.java, (result as Result.Failure).error)
    }

    // ── TROJAN ────────────────────────────────────────────────────────────────

    private val trojanClient = ClientConfig.Trojan(
        password = "s3cr3t",
        flow = "",
        email = "grace",
        enable = true,
        totalGB = 0L,
        expiryTime = 0L,
        limitIp = 0,
        subId = "",
        comment = "",
    )

    @Test
    fun `Trojan TLS TCP - URI starts with trojan scheme and contains password, TLS params`() {
        val streamSettings = """
        {
          "network": "tcp",
          "security": "tls",
          "tcpSettings": {"header": {"type": "none"}},
          "tlsSettings": {
            "serverName": "trojan.example.com",
            "alpn": ["h2", "http/1.1"],
            "settings": {"fingerprint": "chrome"}
          }
        }
        """.trimIndent()

        val inbound = baseInbound.copy(
            protocol = "trojan",
            port = 8443,
            streamSettings = streamSettings,
        )
        val result = ClientUri.build(trojanClient, inbound, "trojan.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data

        assertTrue(uri.startsWith("trojan://s3cr3t@trojan.example.com:8443"),
            "URI must start with trojan://password@host:port")

        val params = parseQueryParams(uri)
        assertEquals("tcp", params["type"])
        assertEquals("tls", params["security"])
        assertEquals("trojan.example.com", params["sni"])
        assertEquals("h2,http/1.1", params["alpn"])
        assertEquals("chrome", params["fp"])

        val fragment = URI(uri.replace(" ", "%20")).fragment
        assertEquals("my-panel-grace", fragment)
    }

    @Test
    fun `Trojan Reality TCP - includes flow param`() {
        val streamSettings = """
        {
          "network": "tcp",
          "security": "reality",
          "tcpSettings": {"header": {"type": "none"}},
          "realitySettings": {
            "serverNames": ["reality.example.com"],
            "shortIds": ["deadbeef"],
            "settings": {"publicKey": "pubkey456", "fingerprint": "safari"}
          }
        }
        """.trimIndent()

        val client = trojanClient.copy(flow = "xtls-rprx-vision")
        val inbound = baseInbound.copy(protocol = "trojan", streamSettings = streamSettings)
        val result = ClientUri.build(client, inbound, "h.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val params = parseQueryParams((result as Result.Success).data)
        assertEquals("reality", params["security"])
        assertEquals("xtls-rprx-vision", params["flow"])
        assertEquals("pubkey456", params["pbk"])
    }

    @Test
    fun `Trojan WS TLS - no flow param (WS transport)`() {
        val streamSettings = """
        {
          "network": "ws",
          "security": "tls",
          "wsSettings": {"path": "/ws", "host": "ws.example.com"},
          "tlsSettings": {"serverName": "ws.example.com", "alpn": ["h2"], "settings": {}}
        }
        """.trimIndent()

        val client = trojanClient.copy(flow = "xtls-rprx-vision")
        val inbound = baseInbound.copy(protocol = "trojan", streamSettings = streamSettings)
        val result = ClientUri.build(client, inbound, "ws.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val params = parseQueryParams((result as Result.Success).data)
        assertEquals("ws", params["type"])
        assertTrue(!params.containsKey("flow"), "flow must be absent for WS transport")
    }

    @Test
    fun `Trojan no security - security=none in params`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val inbound = baseInbound.copy(protocol = "trojan", streamSettings = streamSettings)
        val result = ClientUri.build(trojanClient, inbound, "host")

        assertInstanceOf(Result.Success::class.java, result)
        val params = parseQueryParams((result as Result.Success).data)
        assertEquals("none", params["security"])
    }

    // ── HYSTERIA ──────────────────────────────────────────────────────────────

    private val hysteriaClient = ClientConfig.Hysteria(
        auth = "my-auth-string",
        email = "henry",
        enable = true,
        totalGB = 0L,
        expiryTime = 0L,
        limitIp = 0,
        subId = "",
        comment = "",
    )

    private val hysteriaStreamSettings = """
    {
      "network": "hysteria",
      "security": "tls",
      "tlsSettings": {
        "serverName": "hy2.example.com",
        "alpn": ["h3"],
        "settings": {"fingerprint": "chrome", "allowInsecure": false}
      }
    }
    """.trimIndent()

    @Test
    fun `Hysteria2 - URI scheme is hysteria2 with correct auth, host, TLS params`() {
        val inbound = baseInbound.copy(
            protocol = "hysteria",
            port = 8443,
            settings = """{"version": 2, "clients": []}""",
            streamSettings = hysteriaStreamSettings,
        )
        val result = ClientUri.build(hysteriaClient, inbound, "hy2.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data

        assertTrue(uri.startsWith("hysteria2://my-auth-string@hy2.example.com:8443"),
            "URI must start with hysteria2://auth@host:port but was: $uri")

        val params = parseQueryParams(uri)
        assertEquals("tls", params["security"])
        assertEquals("hy2.example.com", params["sni"])
        assertEquals("h3", params["alpn"])
        assertEquals("chrome", params["fp"])
        assertTrue(!params.containsKey("insecure"), "insecure must be absent when allowInsecure=false")

        val fragment = URI(uri.replace(" ", "%20")).fragment
        assertEquals("my-panel-henry", fragment)
    }

    @Test
    fun `Hysteria v1 - URI scheme is hysteria`() {
        val inbound = baseInbound.copy(
            protocol = "hysteria",
            port = 4430,
            settings = """{"version": 1, "clients": []}""",
            streamSettings = hysteriaStreamSettings,
        )
        val result = ClientUri.build(hysteriaClient, inbound, "hy1.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = (result as Result.Success).data
        assertTrue(uri.startsWith("hysteria://"), "URI must use hysteria:// for v1 but was: $uri")
    }

    @Test
    fun `Hysteria2 insecure=1 when allowInsecure=true`() {
        val streamSettings = """
        {
          "network": "hysteria",
          "security": "tls",
          "tlsSettings": {
            "serverName": "hy.example.com",
            "alpn": ["h3"],
            "settings": {"fingerprint": "chrome", "allowInsecure": true}
          }
        }
        """.trimIndent()

        val inbound = baseInbound.copy(
            protocol = "hysteria",
            settings = """{"version": 2, "clients": []}""",
            streamSettings = streamSettings,
        )
        val result = ClientUri.build(hysteriaClient, inbound, "hy.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val params = parseQueryParams((result as Result.Success).data)
        assertEquals("1", params["insecure"])
    }

    @Test
    fun `Hysteria2 salamander obfs - includes obfs and obfs-password`() {
        val streamSettings = """
        {
          "network": "hysteria",
          "security": "tls",
          "tlsSettings": {"serverName": "hy.example.com", "alpn": ["h3"], "settings": {}},
          "finalmask": {
            "udp": [
              {"type": "salamander", "settings": {"password": "obfspw123"}}
            ]
          }
        }
        """.trimIndent()

        val inbound = baseInbound.copy(
            protocol = "hysteria",
            settings = """{"version": 2, "clients": []}""",
            streamSettings = streamSettings,
        )
        val result = ClientUri.build(hysteriaClient, inbound, "hy.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        val params = parseQueryParams((result as Result.Success).data)
        assertEquals("salamander", params["obfs"])
        assertEquals("obfspw123", params["obfs-password"])
    }

    @Test
    fun `Hysteria version absent in settings defaults to hysteria2 scheme`() {
        val inbound = baseInbound.copy(
            protocol = "hysteria",
            settings = """{"clients": []}""",
            streamSettings = hysteriaStreamSettings,
        )
        val result = ClientUri.build(hysteriaClient, inbound, "hy.example.com")

        assertInstanceOf(Result.Success::class.java, result)
        assertTrue((result as Result.Success).data.startsWith("hysteria2://"))
    }

    // ── Unsupported transport ─────────────────────────────────────────────────

    @Test
    fun `KCP transport returns UnsupportedTransport failure`() {
        val streamSettings = """{"network":"kcp","security":"none","kcpSettings":{"header":{"type":"none"}}}"""
        val inbound = baseInbound.copy(streamSettings = streamSettings)

        val result = ClientUri.build(vlessClient, inbound, "panel.example.com")

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(ShareError.UnsupportedTransport::class.java, error)
        assertEquals("kcp", (error as ShareError.UnsupportedTransport).network)
    }

    // ── Remark format ─────────────────────────────────────────────────────────

    @Test
    fun `remark with empty inbound remark is just email`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val inbound = baseInbound.copy(remark = "", streamSettings = streamSettings)

        val result = ClientUri.build(vlessClient.copy(email = "frank"), inbound, "host")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = URI((result as Result.Success).data)
        assertEquals("frank", uri.fragment)
    }

    @Test
    fun `remark with empty email is just inbound remark`() {
        val streamSettings = """{"network":"tcp","security":"none","tcpSettings":{"header":{"type":"none"}}}"""
        val inbound = baseInbound.copy(remark = "panel-x", streamSettings = streamSettings)

        val result = ClientUri.build(vlessClient.copy(email = ""), inbound, "host")

        assertInstanceOf(Result.Success::class.java, result)
        val uri = URI((result as Result.Success).data)
        assertEquals("panel-x", uri.fragment)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses query parameters from a URI string, handling percent-encoding. */
    private fun parseQueryParams(uriStr: String): Map<String, String> {
        val rawQuery = uriStr.substringAfter("?", "").substringBefore("#")
        if (rawQuery.isEmpty()) return emptyMap()
        return rawQuery.split("&").associate { pair ->
            val (k, v) = pair.split("=", limit = 2)
            java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
        }
    }
}
