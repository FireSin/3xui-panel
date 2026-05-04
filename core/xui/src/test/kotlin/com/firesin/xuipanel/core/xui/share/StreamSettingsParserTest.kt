package com.firesin.xuipanel.core.xui.share

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.xui.dto.Security
import com.firesin.xuipanel.core.xui.dto.StreamSettings
import com.firesin.xuipanel.core.xui.dto.StreamSettingsParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun loadFixture(name: String): String =
    checkNotNull(StreamSettingsParserTest::class.java.classLoader?.getResourceAsStream("share/$name")) {
        "Missing fixture: share/$name"
    }.bufferedReader().readText()

class StreamSettingsParserTest {

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    fun `VLESS Reality TCP - parses all public fields correctly`() {
        val raw = loadFixture("vless_reality_tcp.json")
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Success::class.java, result)
        val stream = (result as Result.Success).data
        assertInstanceOf(StreamSettings.Tcp::class.java, stream)

        val tcp = stream as StreamSettings.Tcp
        assertEquals(null, tcp.headerType, "plain TCP should have null headerType")

        val reality = tcp.security
        assertInstanceOf(Security.Reality::class.java, reality)
        reality as Security.Reality

        // serverNames[0] — parser picks index 0, not random (divergence from upstream)
        assertEquals("example.com", reality.sni)
        // shortIds[0]
        assertEquals("a1b2c3d4", reality.sid)
        assertEquals("abc123publicKeyBase64url", reality.pbk)
        assertEquals("chrome", reality.fp)

        // Private fields must NOT be accessible — they are not part of the data class
        // (compile-time guarantee; no runtime check needed)
    }

    @Test
    fun `VLESS TLS TCP - parses sni, alpn, fingerprint`() {
        val raw = loadFixture("vless_tls_tcp.json")
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Success::class.java, result)
        val stream = (result as Result.Success).data as StreamSettings.Tcp
        val tls = stream.security as Security.Tls

        assertEquals("example.com", tls.sni)
        assertEquals(listOf("http/1.1"), tls.alpn)
        assertEquals("chrome", tls.fingerprint)
    }

    @Test
    fun `WS TLS - parses path and host`() {
        val raw = loadFixture("vless_ws_tls.json")
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Success::class.java, result)
        val stream = (result as Result.Success).data as StreamSettings.Ws

        assertEquals("/api", stream.path)
        assertEquals("example.com", stream.host)

        val tls = stream.security as Security.Tls
        assertEquals("example.com", tls.sni)
    }

    @Test
    fun `gRPC TLS - parses serviceName, authority null when empty, multiMode false`() {
        val raw = loadFixture("vless_grpc_tls.json")
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Success::class.java, result)
        val stream = (result as Result.Success).data as StreamSettings.Grpc

        assertEquals("svc", stream.serviceName)
        assertEquals(null, stream.authority, "empty authority should be null")
        assertEquals(false, stream.multiMode)

        val tls = stream.security as Security.Tls
        assertEquals("example.com", tls.sni)
        assertEquals(listOf("h2"), tls.alpn)
        assertEquals("firefox", tls.fingerprint)
    }

    // ── Negative paths ────────────────────────────────────────────────────────

    @Test
    fun `KCP network returns UnsupportedTransport failure`() {
        val raw = loadFixture("kcp_unsupported.json")
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(ShareError.UnsupportedTransport::class.java, error)
        assertEquals("kcp", (error as ShareError.UnsupportedTransport).network)
    }

    @Test
    fun `malformed JSON returns InvalidStreamSettings failure`() {
        val result = StreamSettingsParser.parse("{not valid json")

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(ShareError.InvalidStreamSettings::class.java, error)
    }

    @Test
    fun `missing network field returns InvalidStreamSettings failure`() {
        val result = StreamSettingsParser.parse("""{"security":"none"}""")

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(ShareError.InvalidStreamSettings::class.java, error)
        assertTrue((error as ShareError.InvalidStreamSettings).reason.contains("network"))
    }

    @Test
    fun `Reality private fields are silently ignored — no exception`() {
        // privateKey + mldsa65Seed present in the fixture, parser must not throw
        val raw = loadFixture("vless_reality_tcp.json")
        val result = StreamSettingsParser.parse(raw)
        // Already tested above; key assertion here is no exception propagation
        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `unknown network returns UnsupportedTransport`() {
        val raw = """{"network":"quic","security":"none"}"""
        val result = StreamSettingsParser.parse(raw)

        assertInstanceOf(Result.Failure::class.java, result)
        assertEquals("quic", ((result as Result.Failure).error as ShareError.UnsupportedTransport).network)
    }
}
