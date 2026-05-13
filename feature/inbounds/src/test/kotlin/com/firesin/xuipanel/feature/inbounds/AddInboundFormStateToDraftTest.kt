package com.firesin.xuipanel.feature.inbounds

import com.firesin.xuipanel.core.xui.draft.ProtocolSettings
import com.firesin.xuipanel.core.xui.draft.SecurityConfig
import com.firesin.xuipanel.core.xui.draft.TransportConfig
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.NetworkType
import com.firesin.xuipanel.feature.inbounds.add.ui.ProtocolType
import com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType
import com.firesin.xuipanel.feature.inbounds.add.ui.ShadowsocksClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.VlessClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.toInboundDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `sniffing config is reflected in draft`() {
        val state = AddInboundFormState(
            selectedProtocol = ProtocolType.VLESS,
            sniffingEnabled = false,
        )

        val draft = state.toInboundDraft()
        assertEquals(false, draft.sniffing.enabled)
    }
}
