package com.firesin.xuipanel.feature.inbounds

import com.firesin.xuipanel.core.xui.draft.ProtocolSettings
import com.firesin.xuipanel.core.xui.draft.SecurityConfig
import com.firesin.xuipanel.core.xui.draft.TransportConfig
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.HysteriaClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.NetworkType
import com.firesin.xuipanel.feature.inbounds.add.ui.ProtocolType
import com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType
import com.firesin.xuipanel.feature.inbounds.add.ui.ShadowsocksClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.VlessClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.toInboundDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddInboundFormStateToDraftTest {

    @Test
    fun `vless + reality + tcp form state converts to correct draft`() {
        val state = AddInboundFormState(
            remark = "vless-reality",
            port = "443",
            selectedProtocol = ProtocolType.VLESS,
            vlessClients = listOf(
                VlessClientState(id = "test-uuid", email = "user@test.com", flow = "xtls-rprx-vision"),
            ),
            selectedNetwork = NetworkType.TCP,
            selectedSecurity = SecurityType.REALITY,
            realityDest = "yahoo.com:443",
            realityServerNames = "yahoo.com\nyahoo.co.jp",
            realityPrivateKey = "priv",
            realityPublicKey = "pub",
            realityShortIds = "abc12345",
            realityFingerprint = "chrome",
        )

        val draft = state.toInboundDraft()

        assertEquals("vless-reality", draft.remark)
        assertEquals(443, draft.port)

        val protocol = draft.protocol as ProtocolSettings.Vless
        assertEquals(1, protocol.clients.size)
        assertEquals("test-uuid", protocol.clients[0].id)
        assertEquals("user@test.com", protocol.clients[0].email)
        assertEquals("xtls-rprx-vision", protocol.clients[0].flow)

        assertNotNull(draft.stream)
        assertInstanceOf(TransportConfig.Tcp::class.java, draft.stream?.transport)
        assertInstanceOf(SecurityConfig.Reality::class.java, draft.stream?.security)

        val reality = draft.stream?.security as SecurityConfig.Reality
        assertEquals("yahoo.com:443", reality.dest)
        assertEquals(listOf("yahoo.com", "yahoo.co.jp"), reality.serverNames)
        assertEquals("priv", reality.privateKey)
        assertEquals("pub", reality.publicKey)
        assertEquals(listOf("abc12345"), reality.shortIds)
        assertEquals("chrome", reality.fingerprint)
    }

    @Test
    fun `shadowsocks form state builds correct draft`() {
        val state = AddInboundFormState(
            remark = "ss-test",
            port = "8388",
            selectedProtocol = ProtocolType.SHADOWSOCKS,
            ssMethod = "chacha20-ietf-poly1305",
            ssPassword = "secret-pass",
            ssNetwork = "tcp,udp",
            ssClients = listOf(
                ShadowsocksClientState(password = "client-pass", email = "ss@test.com"),
            ),
            selectedNetwork = NetworkType.TCP,
            selectedSecurity = SecurityType.NONE,
        )

        val draft = state.toInboundDraft()

        assertEquals("ss-test", draft.remark)
        assertEquals(8388, draft.port)

        val protocol = draft.protocol as ProtocolSettings.Shadowsocks
        assertEquals("chacha20-ietf-poly1305", protocol.method)
        assertEquals("secret-pass", protocol.password)
        assertEquals("tcp,udp", protocol.network)
        assertEquals(1, protocol.clients.size)
        assertEquals("client-pass", protocol.clients[0].password)
        assertEquals("ss@test.com", protocol.clients[0].email)

        // Shadowsocks has stream in our implementation
        assertNotNull(draft.stream)
        val security = draft.stream?.security
        assertInstanceOf(SecurityConfig.None::class.java, security)
    }

    @Test
    fun `socks protocol has no stream config`() {
        val state = AddInboundFormState(
            selectedProtocol = ProtocolType.SOCKS,
            socksAuth = "noauth",
        )

        val draft = state.toInboundDraft()

        assertNull(draft.stream)
        assertInstanceOf(ProtocolSettings.Socks::class.java, draft.protocol)
    }

    @Test
    fun `tun form state converts to correct draft with empty routes`() {
        val state = AddInboundFormState(
            remark = "tun-test",
            port = "0",
            selectedProtocol = ProtocolType.TUN,
            tunMtu = "1500",
            tunGso = false,
            tunGro = false,
            tunEnableExFilter = false,
            tunStrictRoute = true,
            tunRouteAddress = "",
            tunRouteAddressSet = "",
            tunRouteExcludeAddress = "",
            tunRouteExcludeAddressSet = "",
        )

        val draft = state.toInboundDraft()
        assertNull(draft.stream)
        val tun = draft.protocol as ProtocolSettings.Tun
        assertEquals(1500, tun.mtu)
        assertFalse(tun.gso)
        assertTrue(tun.strictRoute)
        assertEquals(emptyList<String>(), tun.routeAddress)
    }

    @Test
    fun `tun form state converts route address strings to lists`() {
        val state = AddInboundFormState(
            selectedProtocol = ProtocolType.TUN,
            tunRouteAddress = "10.0.0.0/8, 192.168.0.0/16",
            tunRouteExcludeAddress = "8.8.8.8/32",
        )

        val draft = state.toInboundDraft()
        val tun = draft.protocol as ProtocolSettings.Tun
        assertEquals(listOf("10.0.0.0/8", "192.168.0.0/16"), tun.routeAddress)
        assertEquals(listOf("8.8.8.8/32"), tun.routeExcludeAddress)
    }

    @Test
    fun `hysteria form state produces hysteria protocol with forced tls stream`() {
        val state = AddInboundFormState(
            remark = "hysteria-test",
            port = "443",
            selectedProtocol = ProtocolType.HYSTERIA,
            hysteriaObfsPassword = "obfs-pass",
            hysteriaUdpIdleTimeout = "60",
            hysteriaClients = listOf(
                HysteriaClientState(auth = "secret123", email = "user@example.com"),
            ),
            tlsServerName = "example.com",
            tlsCertificateFile = "/etc/cert.pem",
            tlsKeyFile = "/etc/key.pem",
        )

        val draft = state.toInboundDraft()
        val hysteria = draft.protocol as ProtocolSettings.Hysteria
        assertEquals(2, hysteria.version)
        assertEquals(1, hysteria.clients.size)
        assertEquals("secret123", hysteria.clients[0].auth)
        assertEquals("user@example.com", hysteria.clients[0].email)

        assertNotNull(draft.stream)
        val transport = draft.stream?.transport as TransportConfig.HysteriaTransport
        assertEquals("obfs-pass", transport.auth)
        assertEquals(60, transport.udpIdleTimeout)

        val tls = draft.stream?.security as SecurityConfig.Tls
        assertEquals("example.com", tls.serverName)
        assertEquals(listOf("h3"), tls.alpn)
        assertEquals(1, tls.certificates.size)
    }

    @Test
    fun `sniffing config is reflected in draft`() {
        val state = AddInboundFormState(
            selectedProtocol = ProtocolType.VLESS,
            sniffingEnabled = false,
        )

        val draft = state.toInboundDraft()
        assertEquals(false, draft.sniffing.enabled)
    }
}
