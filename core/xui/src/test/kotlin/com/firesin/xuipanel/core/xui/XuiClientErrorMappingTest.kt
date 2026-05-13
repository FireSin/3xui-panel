package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.network.tls.SpkiPinMismatchException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLException

/**
 * Tests for XuiClient's exception-to-DomainError mapping (round-5 TLS pinning fix).
 * Verifies cause-chain unwrapping of SpkiPinMismatchException within SSLException
 * and correct PinMismatchEvent emission.
 *
 * Approach: Mock OkHttpClient.newCall() to throw exceptions from different layers.
 * This simulates real SSL/network failures and verifies the mapping logic.
 */
class XuiClientErrorMappingTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var pinMismatchEvents: PinMismatchEventDispatcher
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
        pinMismatchEvents = spyk(PinMismatchEventDispatcher())
        xuiClient = XuiClient(clientFactory, sessionCache, pinMismatchEvents)
    }

    @Test
    fun `fetchInbounds - direct SpkiPinMismatchException maps to DomainError PinMismatch`() = runTest {
        val panelId = "panel-123"
        val observedSpki = "observed-spki-abc123=="
        val pinEx = SpkiPinMismatchException(observedSpki)

        mockClientToThrow(pinEx)

        val result = xuiClient.fetchInbounds(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned-abc=="),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `fetchInbounds - SSLHandshakeException wrapping SpkiPinMismatchException unwraps`() = runTest {
        val panelId = "panel-456"
        val observedSpki = "unwrapped-spki-xyz=="

        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL handshake failed").initCause(pinEx) as SSLException

        mockClientToThrow(sslEx)

        val result = xuiClient.fetchInbounds(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned-xyz=="),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `fetchInbounds - deeply nested SpkiPinMismatchException in cause chain is detected`() = runTest {
        val panelId = "panel-deep"
        val observedSpki = "deep-nested-spki=="

        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL")
        val ioEx = java.io.IOException("IO error").initCause(sslEx) as java.io.IOException
        ioEx.initCause(pinEx)

        mockClientToThrow(ioEx)

        val result = xuiClient.fetchInbounds(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned-xyz=="),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `fetchInbounds - SSLException without pin mismatch maps to DomainError Tls`() = runTest {
        val sslEx = SSLException("Certificate untrusted")

        mockClientToThrow(sslEx)

        val result = xuiClient.fetchInbounds(
            panelId = "panel-tls-error",
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.SYSTEM, null),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.Tls::class.java, error)
        val tlsError = error as DomainError.Tls
        assertEquals("Certificate untrusted", tlsError.message)
    }

    @Test
    fun `setInboundEnabled - SpkiPinMismatchException in cause chain maps correctly`() = runTest {
        val panelId = "panel-toggle"
        val observedSpki = "toggle-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException

        mockClientToThrow(sslEx)

        val result = xuiClient.setInboundEnabled(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned=="),
            enabled = true,
            id = 1,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `deleteInbound - SpkiPinMismatchException in cause chain maps correctly`() = runTest {
        val panelId = "panel-delete"
        val observedSpki = "delete-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException

        mockClientToThrow(sslEx)

        val result = xuiClient.deleteInbound(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned=="),
            id = 2,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
    }

    @Test
    fun `fetchServerStatus - SSLException wrapping SpkiPinMismatchException unwraps`() = runTest {
        val panelId = "panel-status"
        val observedSpki = "status-spki=="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException

        mockClientToThrow(sslEx)

        val result = xuiClient.fetchServerStatus(
            panelId = panelId,
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.PINNED, "pinned=="),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals(panelId, pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `probeLogin - direct SpkiPinMismatchException maps with empty panelId`() = runTest {
        val observedSpki = "probe-spki="
        val pinEx = SpkiPinMismatchException(observedSpki)

        mockProbeClientToThrow(pinEx)

        val creds = ProbeCredentials(
            baseUrl = "https://example.com",
            login = "admin",
            password = "secret",
            tlsMode = TlsMode.PINNED,
            pinnedSpkiSha256 = "expected-spki==",
        )

        val result = xuiClient.probeLogin(creds)

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals("", pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `probeLogin - SSLException wrapping SpkiPinMismatchException unwraps with empty panelId`() = runTest {
        val observedSpki = "probe-wrapped-spki="
        val pinEx = SpkiPinMismatchException(observedSpki)
        val sslEx = SSLException("SSL").initCause(pinEx) as SSLException

        mockProbeClientToThrow(sslEx)

        val creds = ProbeCredentials(
            baseUrl = "https://example.com",
            login = "admin",
            password = "secret",
            tlsMode = TlsMode.PINNED,
            pinnedSpkiSha256 = "expected-spki==",
        )

        val result = xuiClient.probeLogin(creds)

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.PinMismatch::class.java, error)
        val pinError = error as DomainError.PinMismatch
        assertEquals("", pinError.panelId)
        assertEquals(observedSpki, pinError.observedSpki)
    }

    @Test
    fun `probeLogin - SSLException without pin mismatch maps to DomainError Tls`() = runTest {
        val sslEx = SSLException("SSL failed")

        mockProbeClientToThrow(sslEx)

        val creds = ProbeCredentials(
            baseUrl = "https://example.com",
            login = "admin",
            password = "secret",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        val result = xuiClient.probeLogin(creds)

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.Tls::class.java, error)
    }

    @Test
    fun `SSLException with null message uses class name fallback in Tls error`() = runTest {
        val sslEx = SSLException(null as String?)

        mockClientToThrow(sslEx)

        val result = xuiClient.fetchInbounds(
            panelId = "panel-null-msg",
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.SYSTEM, null),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.Tls::class.java, error)
        val tlsError = error as DomainError.Tls
        assertEquals("SSLException", tlsError.message)
    }

    @Test
    fun `IOException without pin mismatch maps to DomainError Network`() = runTest {
        val ioEx = java.io.IOException("Network error")

        mockClientToThrow(ioEx)

        val result = xuiClient.fetchInbounds(
            panelId = "panel-network",
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.SYSTEM, null),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.Network::class.java, error)
    }

    @Test
    fun `unexpected exception maps to DomainError Unexpected`() = runTest {
        val unexpectedException = RuntimeException("Unexpected")

        mockClientToThrow(unexpectedException)

        val result = xuiClient.fetchInbounds(
            panelId = "panel-unexpected",
            baseUrl = "https://example.com",
            auth = PanelAuth.Login("user", "pass"),
            tls = PanelTls(TlsMode.SYSTEM, null),
        )

        assertInstanceOf(Result.Failure::class.java, result)
        val error = (result as Result.Failure).error
        assertInstanceOf(DomainError.Unexpected::class.java, error)
    }

    // Mock helpers to inject exceptions at OkHttp layer

    private fun mockClientToThrow(exception: Throwable) {
        val mockCall = mockk<Call>()
        every { mockCall.execute() } throws exception

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any<Request>()) } returns mockCall

        coEvery { clientFactory.getClient(any(), any()) } returns mockClient
        coEvery { sessionCache.get(any()) } returns null
    }

    private fun mockProbeClientToThrow(exception: Throwable) {
        val mockCall = mockk<Call>()
        every { mockCall.execute() } throws exception

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any<Request>()) } returns mockCall

        coEvery { clientFactory.buildTransient(any()) } returns Pair(mockClient, null)
    }
}
