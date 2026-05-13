package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.TlsMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class PanelToAuthTest {

    @Test
    fun `returns Bearer when apiToken is not blank`() {
        val panel = fakePanel(apiToken = "secret-api-token-123")
        val auth = panel.toAuth()
        assertInstanceOf(PanelAuth.Bearer::class.java, auth)
        assertEquals("secret-api-token-123", (auth as PanelAuth.Bearer).token)
    }

    @Test
    fun `returns Login when apiToken is null`() {
        val panel = fakePanel(apiToken = null)
        val auth = panel.toAuth()
        assertInstanceOf(PanelAuth.Login::class.java, auth)
        auth as PanelAuth.Login
        assertEquals("admin", auth.username)
        assertEquals("pass", auth.password)
    }

    @Test
    fun `returns Login when apiToken is blank`() {
        val panel = fakePanel(apiToken = "   ")
        val auth = panel.toAuth()
        assertInstanceOf(PanelAuth.Login::class.java, auth)
    }

    private fun fakePanel(apiToken: String?) = Panel(
        id = "p1",
        name = "Test",
        baseUrl = "https://example.com",
        login = "admin",
        password = "pass",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = true,
        createdAt = Instant.now(),
        lastLoginAt = null,
        apiToken = apiToken,
    )
}
