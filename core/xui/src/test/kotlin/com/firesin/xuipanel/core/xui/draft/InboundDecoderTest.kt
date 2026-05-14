package com.firesin.xuipanel.core.xui.draft

import com.firesin.xuipanel.core.xui.dto.InboundDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Round-trip tests: encode → wrap in InboundDto → decode → re-encode.
 * The final re-encoded DTO must equal the first one (byte-identical JSON strings).
 */
class InboundDecoderTest {

    // ---- helpers ----

    private fun InboundDto.reEncoded() = InboundEncoder.encode(InboundDecoder.decode(this))

    private fun draftToDto(draft: InboundDraft): InboundDto {
        val req = InboundEncoder.encode(draft)
        return InboundDto(
            id = 1,
            up = 0L,
            down = 0L,
            total = draft.total,
            remark = draft.remark,
            enable = draft.enable,
            expiryTime = draft.expiryTime,
            clientStats = null,
            listen = draft.listen,
            port = draft.port,
            protocol = req.protocol,
            settings = req.settings,
            streamSettings = req.streamSettings,
            tag = "inbound-${draft.port}",
            sniffing = req.sniffing,
            nodeId = draft.nodeId,
        )
    }

    @Test
    fun `nodeId null is decoded to null in draft`() {
        val dto = InboundDto(
            id = 10, up = 0L, down = 0L, total = 0L, remark = "local",
            enable = true, expiryTime = 0L, clientStats = null, listen = "",
            port = 443, protocol = "vless", settings = "{}", streamSettings = "{}",
            tag = "inbound-443", sniffing = "{}", nodeId = null,
        )
        val draft = InboundDecoder.decode(dto)
        assertEquals(null, draft.nodeId)
    }

    @Test
    fun `nodeId non-null is decoded into draft`() {
        val dto = InboundDto(
            id = 11, up = 0L, down = 0L, total = 0L, remark = "node-inbound",
            enable = true, expiryTime = 0L, clientStats = null, listen = "",
            port = 8080, protocol = "vmess", settings = "{}", streamSettings = "{}",
            tag = "inbound-8080", sniffing = "{}", nodeId = 3,
        )
        val draft = InboundDecoder.decode(dto)
        assertEquals(3, draft.nodeId)
    }

    // ---- VLESS + Reality + TCP ----

    @Test
    fun `vless reality tcp round-trip is stable`() {
        val draft = InboundDraft(
            remark = "vless-reality",
            port = 443,
            protocol = ProtocolSettings.Vless(
                clients = listOf(
                    VlessClient(
                        id = "uuid-vless-1",
                        email = "user@example.com",
                        flow = "xtls-rprx-vision",
                        totalGB = 10L * 1_073_741_824L,
                        expiryTime = 0L,
                        limitIp = 2,
                        subId = "sub1",
                        tgId = "",
                        comment = "",
                        reset = 0,
                        enable = true,
                    ),
                ),
                decryption = "none",
                fallbacks = emptyList(),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Tcp(),
                security = SecurityConfig.Reality(
                    dest = "yahoo.com:443",
                    serverNames = listOf("yahoo.com"),
                    privateKey = "privKey123",
                    publicKey = "pubKey456",
                    shortIds = listOf("aabbccdd"),
                    fingerprint = "chrome",
                ),
            ),
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- VMESS + WS + TLS ----

    @Test
    fun `vmess ws tls round-trip is stable`() {
        val draft = InboundDraft(
            remark = "vmess-ws-tls",
            port = 8443,
            protocol = ProtocolSettings.Vmess(
                clients = listOf(
                    VmessClient(
                        id = "vmess-uuid-1",
                        email = "v@example.com",
                        totalGB = 0L,
                        expiryTime = 0L,
                        enable = true,
                        subId = "",
                        tgId = "",
                        comment = "",
                        reset = 0,
                        limitIp = 0,
                    ),
                ),
                disableInsecureEncryption = true,
            ),
            stream = StreamConfig(
                transport = TransportConfig.Ws(path = "/ray", host = "cdn.example.com"),
                security = SecurityConfig.Tls(
                    serverName = "cdn.example.com",
                    minVersion = "1.2",
                    maxVersion = "1.3",
                    alpn = listOf("h2", "http/1.1"),
                    fingerprint = "chrome",
                ),
            ),
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- TROJAN + gRPC + None ----

    @Test
    fun `trojan grpc none round-trip is stable`() {
        val draft = InboundDraft(
            remark = "trojan-grpc",
            port = 443,
            protocol = ProtocolSettings.Trojan(
                clients = listOf(
                    TrojanClient(
                        password = "trojan-pass",
                        email = "t@example.com",
                        flow = "",
                        totalGB = 0L,
                        expiryTime = 0L,
                        enable = true,
                        subId = "sub2",
                        tgId = "",
                        comment = "",
                        reset = 0,
                        limitIp = 0,
                    ),
                ),
                fallbacks = listOf(Fallback(dest = "80")),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Grpc(serviceName = "TunnelService", authority = "", multiMode = false),
                security = SecurityConfig.None,
            ),
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- SHADOWSOCKS + TCP + None ----

    @Test
    fun `shadowsocks tcp none round-trip is stable`() {
        val draft = InboundDraft(
            remark = "ss-inbound",
            port = 8388,
            protocol = ProtocolSettings.Shadowsocks(
                method = "2022-blake3-aes-128-gcm",
                password = "inbound-password",
                network = "tcp,udp",
                clients = listOf(
                    ShadowsocksClient(
                        password = "client-pass",
                        email = "ss@example.com",
                        totalGB = 0L,
                        expiryTime = 0L,
                        enable = true,
                        subId = "",
                        tgId = "",
                        comment = "",
                        reset = 0,
                        limitIp = 0,
                        method = "",
                    ),
                ),
            ),
            stream = StreamConfig(
                transport = TransportConfig.Tcp(),
                security = SecurityConfig.None,
            ),
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- WIREGUARD (no stream) ----

    @Test
    fun `wireguard no-stream round-trip is stable`() {
        val draft = InboundDraft(
            remark = "wg-inbound",
            port = 51820,
            protocol = ProtocolSettings.Wireguard(
                secretKey = "secret-key-base64",
                mtu = 1420,
                noKernelTun = false,
                peers = listOf(
                    WgPeer(
                        publicKey = "peer-pub-key",
                        allowedIPs = listOf("0.0.0.0/0", "::/0"),
                        presharedKey = "",
                        keepAlive = 0,
                    ),
                ),
            ),
            stream = null,
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- MIXED/SOCKS (no stream) ----

    @Test
    fun `mixed socks no-stream round-trip is stable`() {
        val draft = InboundDraft(
            remark = "socks-inbound",
            port = 1080,
            protocol = ProtocolSettings.Socks(
                auth = "password",
                accounts = listOf(UserPass(user = "user1", pass = "pass1")),
                udp = true,
                ip = "127.0.0.1",
            ),
            stream = null,
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- HTTP (no stream) ----

    @Test
    fun `http no-stream round-trip is stable`() {
        val draft = InboundDraft(
            remark = "http-proxy",
            port = 8080,
            protocol = ProtocolSettings.Http(
                accounts = listOf(UserPass(user = "admin", pass = "secret")),
                allowTransparent = false,
            ),
            stream = null,
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- TUNNEL/DOKODEMO (no stream) ----

    @Test
    fun `tunnel dokodemo no-stream round-trip is stable`() {
        val draft = InboundDraft(
            remark = "tunnel-inbound",
            port = 12345,
            protocol = ProtocolSettings.Dokodemo(
                address = "192.168.1.1",
                targetPort = 80,
                network = "tcp,udp",
                followRedirect = false,
            ),
            stream = null,
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- Real server JSON fixture: VLESS + Reality + TCP ----

    @Test
    fun `decode real server json vless reality tcp`() {
        // JSON captured from a real 3x-ui panel response
        val settings = """{"clients":[{"id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","flow":"xtls-rprx-vision","email":"user@test.com","limitIp":0,"totalGB":0,"expiryTime":0,"enable":true,"tgId":"","subId":"abc123","comment":"","reset":0}],"decryption":"none","fallbacks":[]}"""
        val streamSettings = """{"network":"tcp","security":"reality","realitySettings":{"show":false,"xver":0,"dest":"yahoo.com:443","serverNames":["yahoo.com"],"privateKey":"priv_key_here","shortIds":["aabbccdd11223344"],"minClient":"","maxClient":"","maxTimediff":0,"settings":{"publicKey":"pub_key_here","fingerprint":"chrome"}}}"""
        val sniffing = """{"enabled":true,"destOverride":["http","tls","quic"],"metadataOnly":false,"routeOnly":false}"""

        val dto = InboundDto(
            id = 42,
            up = 100_000L,
            down = 500_000L,
            total = 0L,
            remark = "vless-reality-prod",
            enable = true,
            expiryTime = 0L,
            clientStats = null,
            listen = "",
            port = 443,
            protocol = "vless",
            settings = settings,
            streamSettings = streamSettings,
            tag = "inbound-443",
            sniffing = sniffing,
        )

        val draft = InboundDecoder.decode(dto)

        assertEquals("vless-reality-prod", draft.remark)
        assertEquals(443, draft.port)
        val vless = draft.protocol as ProtocolSettings.Vless
        assertEquals(1, vless.clients.size)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", vless.clients[0].id)
        assertEquals("xtls-rprx-vision", vless.clients[0].flow)
        assertEquals("user@test.com", vless.clients[0].email)
        assertEquals("none", vless.decryption)

        val stream = draft.stream!!
        val tcp = stream.transport as TransportConfig.Tcp
        assertEquals(TcpHeader.None, tcp.header)

        val reality = stream.security as SecurityConfig.Reality
        assertEquals("yahoo.com:443", reality.dest)
        assertEquals(listOf("yahoo.com"), reality.serverNames)
        assertEquals("priv_key_here", reality.privateKey)
        assertEquals("pub_key_here", reality.publicKey)
        assertEquals(listOf("aabbccdd11223344"), reality.shortIds)
        assertEquals("chrome", reality.fingerprint)

        // Sniffing
        assertEquals(true, draft.sniffing.enabled)
        assertEquals(listOf("http", "tls", "quic"), draft.sniffing.destOverride)
    }

    // ---- TUN (no stream) ----

    @Test
    fun `tun no-stream round-trip is stable`() {
        val draft = InboundDraft(
            remark = "tun-inbound",
            port = 0,
            protocol = ProtocolSettings.Tun(
                mtu = 1500,
                gso = false,
                gro = false,
                enableExFilter = false,
                strictRoute = true,
                routeAddress = listOf("10.0.0.0/8"),
                routeAddressSet = emptyList(),
                routeExcludeAddress = emptyList(),
                routeExcludeAddressSet = emptyList(),
            ),
            stream = null,
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- HYSTERIA + HysteriaTransport + TLS ----

    @Test
    fun `hysteria tls round-trip is stable`() {
        val draft = InboundDraft(
            remark = "hysteria-inbound",
            port = 443,
            protocol = ProtocolSettings.Hysteria(
                version = 2,
                clients = listOf(
                    HysteriaClient(
                        auth = "secret123",
                        email = "user@example.com",
                        totalGB = 0L,
                        expiryTime = 0L,
                        limitIp = 0,
                        subId = "",
                        tgId = "",
                        comment = "",
                        reset = 0,
                        enable = true,
                    ),
                ),
            ),
            stream = StreamConfig(
                transport = TransportConfig.HysteriaTransport(auth = "obfs-pass", udpIdleTimeout = 60),
                security = SecurityConfig.Tls(
                    serverName = "example.com",
                    minVersion = "1.2",
                    maxVersion = "1.3",
                    alpn = listOf("h3"),
                    fingerprint = "",
                ),
            ),
        )
        val dto = draftToDto(draft)
        val encoded = InboundEncoder.encode(draft)
        val reEncoded = dto.reEncoded()
        assertEquals(encoded, reEncoded)
    }

    // ---- Edge: empty / malformed JSON doesn't crash ----

    @Test
    fun `decode empty json blobs returns sensible defaults`() {
        val dto = InboundDto(
            id = 1,
            up = 0L,
            down = 0L,
            total = 0L,
            remark = "vless-default",
            enable = true,
            expiryTime = 0L,
            clientStats = null,
            listen = "",
            port = 443,
            protocol = "vless",
            settings = "{}",
            streamSettings = "{}",
            tag = "inbound-443",
            sniffing = "{}",
        )
        val draft = InboundDecoder.decode(dto)
        val vless = draft.protocol as ProtocolSettings.Vless
        assertEquals(emptyList<VlessClient>(), vless.clients)
        assertEquals("none", vless.decryption)
        // stream defaults to TCP/none for vless with empty streamSettings
        assertEquals(TransportConfig.Tcp(TcpHeader.None), draft.stream?.transport)
    }
}
