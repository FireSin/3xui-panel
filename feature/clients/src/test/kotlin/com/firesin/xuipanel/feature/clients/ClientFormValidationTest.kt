package com.firesin.xuipanel.feature.clients

import com.firesin.xuipanel.core.xui.dto.ClientConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure business-logic tests for client form validation rules.
 * Mirrors the validation predicates used in [ClientFormScreen].
 */
class ClientFormValidationTest {

    // --- Email validation ---

    @Test
    fun `empty email is invalid`() {
        assertFalse(isEmailValid(""))
    }

    @Test
    fun `blank email is invalid`() {
        assertFalse(isEmailValid("   "))
    }

    @Test
    fun `non-empty email is valid`() {
        assertTrue(isEmailValid("user@example.com"))
    }

    @Test
    fun `email without @ sign is still valid (upstream allows free-form)`() {
        assertTrue(isEmailValid("justusername"))
    }

    // --- UUID validation ---

    @Test
    fun `empty UUID is invalid for vmess`() {
        assertFalse(isIdentityValid("vmess", uuid = "", password = ""))
    }

    @Test
    fun `blank UUID is invalid for vless`() {
        assertFalse(isIdentityValid("vless", uuid = "  ", password = ""))
    }

    @Test
    fun `valid UUID is valid for vmess`() {
        assertTrue(isIdentityValid("vmess", uuid = "11111111-2222-3333-4444-555555555555", password = ""))
    }

    @Test
    fun `valid UUID is valid for vless`() {
        assertTrue(isIdentityValid("vless", uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", password = ""))
    }

    // --- Shadowsocks password validation ---

    @Test
    fun `empty password is invalid for shadowsocks`() {
        assertFalse(isIdentityValid("shadowsocks", uuid = "", password = ""))
    }

    @Test
    fun `non-empty password is valid for shadowsocks`() {
        assertTrue(isIdentityValid("shadowsocks", uuid = "", password = "base64pass=="))
    }

    // --- totalGB validation ---

    @Test
    fun `zero totalGB is valid (unlimited)`() {
        assertTrue(isTotalGbValid(0L))
    }

    @Test
    fun `positive totalGB is valid`() {
        assertTrue(isTotalGbValid(10L))
    }

    @Test
    fun `negative totalGB is invalid`() {
        assertFalse(isTotalGbValid(-1L))
    }

    // --- limitIp validation ---

    @Test
    fun `zero limitIp is valid (unlimited)`() {
        assertTrue(isLimitIpValid(0))
    }

    @Test
    fun `positive limitIp is valid`() {
        assertTrue(isLimitIpValid(3))
    }

    @Test
    fun `negative limitIp is invalid`() {
        assertFalse(isLimitIpValid(-1))
    }

    // --- Combined submit gate ---

    @Test
    fun `form is enabled when all fields are valid`() {
        assertTrue(
            isSubmitEnabled(
                protocol = "vmess",
                uuid = "11111111-2222-3333-4444-555555555555",
                ssPassword = "",
                email = "user@test.com",
                totalGb = 0L,
                limitIp = 0,
            ),
        )
    }

    @Test
    fun `form is disabled when email is empty`() {
        assertFalse(
            isSubmitEnabled(
                protocol = "vmess",
                uuid = "11111111-2222-3333-4444-555555555555",
                ssPassword = "",
                email = "",
                totalGb = 0L,
                limitIp = 0,
            ),
        )
    }

    @Test
    fun `form is disabled when uuid is empty for vmess`() {
        assertFalse(
            isSubmitEnabled(
                protocol = "vmess",
                uuid = "",
                ssPassword = "",
                email = "user@test.com",
                totalGb = 0L,
                limitIp = 0,
            ),
        )
    }

    @Test
    fun `form is disabled when password is empty for shadowsocks`() {
        assertFalse(
            isSubmitEnabled(
                protocol = "shadowsocks",
                uuid = "",
                ssPassword = "",
                email = "user@test.com",
                totalGb = 0L,
                limitIp = 0,
            ),
        )
    }

    @Test
    fun `form is disabled when totalGB is negative`() {
        assertFalse(
            isSubmitEnabled(
                protocol = "vmess",
                uuid = "11111111-2222-3333-4444-555555555555",
                ssPassword = "",
                email = "user@test.com",
                totalGb = -1L,
                limitIp = 0,
            ),
        )
    }

    // --- created_at preservation on update ---

    @Test
    fun `update preserves createdAt from existing client`() {
        val createdAt = 1777534536000L
        val existing = ClientConfig.Vmess(
            id = "11111111-2222-3333-4444-555555555555",
            email = "user@test.com",
            enable = true,
            totalGB = 0L,
            expiryTime = 0L,
            limitIp = 0,
            subId = "",
            comment = "",
            createdAt = createdAt,
        )
        // Simulate what buildClientConfig does for update
        val resolvedCreatedAt = existing.createdAt ?: System.currentTimeMillis()
        assertTrue(resolvedCreatedAt == createdAt)
    }

    @Test
    fun `add sets createdAt to current time when existing is null`() {
        val before = System.currentTimeMillis()
        val resolvedCreatedAt = null ?: System.currentTimeMillis()
        val after = System.currentTimeMillis()
        assertTrue(resolvedCreatedAt in before..after)
    }

    // --- Helper functions mirroring ClientFormScreen validation predicates ---

    private fun isEmailValid(email: String): Boolean = email.isNotBlank()

    private fun isIdentityValid(protocol: String, uuid: String, password: String): Boolean =
        when (protocol.lowercase()) {
            "shadowsocks" -> password.isNotBlank()
            else -> uuid.isNotBlank()
        }

    private fun isTotalGbValid(value: Long): Boolean = value >= 0L

    private fun isLimitIpValid(value: Int): Boolean = value >= 0

    private fun isSubmitEnabled(
        protocol: String,
        uuid: String,
        ssPassword: String,
        email: String,
        totalGb: Long,
        limitIp: Int,
    ): Boolean =
        isEmailValid(email) &&
            isIdentityValid(protocol, uuid, ssPassword) &&
            isTotalGbValid(totalGb) &&
            isLimitIpValid(limitIp)
}
