package com.firesin.xuipanel.core.xui.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientConfigUrlKeyTest {

    @Test
    fun `Vmess urlKey is the id`() {
        val client = ClientConfig.Vmess(
            id = "11111111-2222-3333-4444-555555555555",
            email = "vm@test",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        assertEquals("11111111-2222-3333-4444-555555555555", client.urlKey)
    }

    @Test
    fun `Vless urlKey is the id`() {
        val client = ClientConfig.Vless(
            id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            flow = "xtls-rprx-vision",
            email = "vl@test",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", client.urlKey)
    }

    @Test
    fun `Shadowsocks urlKey is the email`() {
        val client = ClientConfig.Shadowsocks(
            password = "secret",
            method = "chacha20-ietf-poly1305",
            email = "ss@test",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        assertEquals("ss@test", client.urlKey)
    }

    @Test
    fun `Trojan urlKey is the email`() {
        val client = ClientConfig.Trojan(
            password = "trojanpass/special+chars=",
            flow = "",
            email = "tr@test",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        assertEquals("tr@test", client.urlKey)
    }

    @Test
    fun `Hysteria urlKey is the email`() {
        val client = ClientConfig.Hysteria(
            auth = "hysteria/auth+special=chars",
            email = "hy@test",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
        )
        assertEquals("hy@test", client.urlKey)
    }
}
