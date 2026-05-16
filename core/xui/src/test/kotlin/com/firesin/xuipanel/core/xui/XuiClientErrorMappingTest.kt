package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.NoOpPanelLookup
import com.firesin.xuipanel.core.common.twofactor.NoOpTwoFactorOtpBus
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.network.tls.SpkiPinMismatchException
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Direct unit tests for [XuiClient.toDomainError] (member extension) and
 * [toProbeDomainError] (file-level): verifies cause-chain unwrapping of
 * [SpkiPinMismatchException] inside generic SSL/IO wrappers and the final
 * [DomainError] shape returned to callers.
 *
 * Calls the mappers directly — no HTTP, no Retrofit. The previous suite mocked
 * `OkHttpClient.newCall().execute()`, but Retrofit's suspend functions call
 * `enqueue()`, so the mock was never hit and assertions stayed unverified.
 */
class XuiClientErrorMappingTest {

    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        val clientFactory: OkHttpClientFactory = mockk(relaxed = true)
        val sessionCache: XuiSessionCache = mockk(relaxed = true)
        val pinMismatchEvents = spyk(PinMismatchEventDispatcher())
        xuiClient = XuiClient(clientFactory, sessionCache, pinMismatchEvents, WsUiEventDispatcher(), NoOpTwoFactorOtpBus, NoOpPanelLookup)
    }

    // ---- toDomainError ----

    @Test
    fun `toDomainError - direct SpkiPinMismatchException maps to PinMismatch with panelId`() {
        val panelId = "panel-123"
        val observedSpki = "observed-spki-abc123=="
        val ex = SpkiPinMismatchException(observedSpki)

        val result = with(xuiClient) { ex.toDomainError(panelId) }

        val error = result.error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toDomainError - SSLException wrapping SpkiPinMismatchException unwraps`() {
        val panelId = "panel-wrapped"
        val observedSpki = "wrapped-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL handshake failed").initCause(pinEx) as SSLException

        val result = with(xuiClient) { sslEx.toDomainError(panelId) }

        val pinError = result.error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toDomainError - SSLHandshakeException wrapping SpkiPinMismatchException unwraps`() {
        val panelId = "panel-handshake"
        val observedSpki = "handshake-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLHandshakeException("handshake fail").initCause(pinEx) as SSLHandshakeException

        val result = with(xuiClient) { sslEx.toDomainError(panelId) }

        val pinError = result.error as DomainError.PinMismatch
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toDomainError - deeply nested SpkiPinMismatchException is detected`() {
        val panelId = "panel-deep"
        val observedSpki = "deep-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException
        val ioEx = java.io.IOException("IO error").initCause(sslEx) as java.io.IOException

        val result = with(xuiClient) { ioEx.toDomainError(panelId) }

        val pinError = result.error as DomainError.PinMismatch
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toDomainError - SSLException without pin mismatch maps to Tls`() {
        val ex = SSLException("Certificate untrusted")

        val result = with(xuiClient) { ex.toDomainError("any-panel") }

        val tlsError = result.error as DomainError.Tls
        assertEquals("Certificate untrusted", tlsError.message)
    }

    @Test
    fun `toDomainError - SSLException with null message falls back to class name`() {
        val ex = SSLException(null as String?)

        val result = with(xuiClient) { ex.toDomainError("any-panel") }

        val tlsError = result.error as DomainError.Tls
        assertEquals("SSLException", tlsError.message)
    }

    @Test
    fun `toDomainError - IOException without pin mismatch maps to Network`() {
        val ex = java.io.IOException("Network error")

        val result = with(xuiClient) { ex.toDomainError("any-panel") }

        assertInstanceOf(DomainError.Network::class.java, result.error)
    }

    @Test
    fun `toDomainError - unexpected exception maps to Unexpected`() {
        val ex = RuntimeException("Unexpected")

        val result = with(xuiClient) { ex.toDomainError("any-panel") }

        assertInstanceOf(DomainError.Unexpected::class.java, result.error)
    }

    @Test
    fun `toDomainError - XuiAuthException maps to InvalidCredentials`() {
        val ex = XuiAuthException("bad creds")

        val result = with(xuiClient) { ex.toDomainError("any-panel") }

        assertEquals(DomainError.InvalidCredentials, result.error)
    }

    // ---- toProbeDomainError ----

    @Test
    fun `toProbeDomainError - direct SpkiPinMismatchException maps with empty panelId`() {
        val observedSpki = "probe-spki=="
        val ex = SpkiPinMismatchException(observedSpki)

        val result: Result.Failure<DomainError> = ex.toProbeDomainError()

        val pinError = result.error as DomainError.PinMismatch
        assertEquals("", pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toProbeDomainError - SSLException wrapping SpkiPinMismatchException unwraps`() {
        val observedSpki = "probe-wrapped=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException

        val result = sslEx.toProbeDomainError()

        val pinError = result.error as DomainError.PinMismatch
        assertEquals("", pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `toProbeDomainError - SSLException without pin mismatch maps to Tls`() {
        val ex = SSLException("SSL failed")

        val result = ex.toProbeDomainError()

        assertInstanceOf(DomainError.Tls::class.java, result.error)
    }

    @Test
    fun `toProbeDomainError - IOException maps to Network`() {
        val ex = java.io.IOException("network")

        val result = ex.toProbeDomainError()

        assertInstanceOf(DomainError.Network::class.java, result.error)
    }

    @Test
    fun `toProbeDomainError - other exception maps to Unexpected`() {
        val ex = RuntimeException("boom")

        val result = ex.toProbeDomainError()

        assertInstanceOf(DomainError.Unexpected::class.java, result.error)
    }
}
