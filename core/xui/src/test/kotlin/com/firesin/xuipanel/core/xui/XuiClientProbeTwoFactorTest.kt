package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import io.mockk.just
import io.mockk.Runs
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class XuiClientProbeTwoFactorTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
        xuiClient = XuiClient(clientFactory, sessionCache, spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher())
    }

    @Test
    fun `probeTwoFactorEnabled returns true when obj is true`() = runTest {
        mockTransientResponse(200, """{"success":true,"obj":true}""")

        val result = xuiClient.probeTwoFactorEnabled(
            baseUrl = "https://panel.example.com:2053/",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        assertInstanceOf(Result.Success::class.java, result)
        assertEquals(true, (result as Result.Success).data)
    }

    @Test
    fun `probeTwoFactorEnabled returns false when obj is false`() = runTest {
        mockTransientResponse(200, """{"success":true,"obj":false}""")

        val result = xuiClient.probeTwoFactorEnabled(
            baseUrl = "https://panel.example.com:2053/",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        assertInstanceOf(Result.Success::class.java, result)
        assertEquals(false, (result as Result.Success).data)
    }

    @Test
    fun `probeTwoFactorEnabled returns false when success is false`() = runTest {
        mockTransientResponse(200, """{"success":false}""")

        val result = xuiClient.probeTwoFactorEnabled(
            baseUrl = "https://panel.example.com:2053/",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        assertInstanceOf(Result.Success::class.java, result)
        assertEquals(false, (result as Result.Success).data)
    }

    @Test
    fun `probeTwoFactorEnabled returns PanelUnreachable on non-2xx`() = runTest {
        mockTransientResponse(503, """{"success":false}""")

        val result = xuiClient.probeTwoFactorEnabled(
            baseUrl = "https://panel.example.com:2053/",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        assertInstanceOf(DomainError.PanelUnreachable::class.java, (result as Result.Failure).error)
    }

    @Test
    fun `probeTwoFactorEnabled returns Network failure on IOException`() = runTest {
        mockTransientThrow(IOException("connection refused"))

        val result = xuiClient.probeTwoFactorEnabled(
            baseUrl = "https://panel.example.com:2053/",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        assertInstanceOf(DomainError.Network::class.java, (result as Result.Failure).error)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockTransientResponse(code: Int, body: String) {
        // probeTwoFactorEnabled now does GET /csrf-token first, then POST
        // /getTwoFactorEnable with the X-CSRF-Token header. Both calls hit this
        // mock, so we route by request path: csrf-token gets a stub Success body
        // and the real expectation goes to the 2FA endpoint.
        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val responseBody = if (request.url.encodedPath.endsWith("/csrf-token")) {
                """{"success":true,"obj":"stub-csrf"}"""
            } else {
                body
            }
            val responseCode = if (request.url.encodedPath.endsWith("/csrf-token")) 200 else code
            val fakeResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode in 200..299) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
            val mockCall = mockk<Call>()
            every { mockCall.execute() } returns fakeResponse
            every { mockCall.cancel() } just Runs
            every { mockCall.isExecuted() } returns false
            every { mockCall.isCanceled() } returns false
            every { mockCall.enqueue(any()) } answers {
                firstArg<okhttp3.Callback>().onResponse(mockCall, fakeResponse)
            }
            mockCall
        }
        coEvery { clientFactory.buildTransient(any()) } returns Pair(mockClient, null)
    }

    private fun mockTransientThrow(exception: Throwable) {
        val mockCall = mockk<Call>()
        every { mockCall.execute() } throws exception
        every { mockCall.cancel() } just Runs
        every { mockCall.isExecuted() } returns false
        every { mockCall.isCanceled() } returns false
        every { mockCall.enqueue(any()) } answers {
            firstArg<okhttp3.Callback>().onFailure(mockCall, exception as? IOException ?: IOException(exception))
        }

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()

        coEvery { clientFactory.buildTransient(any()) } returns Pair(mockClient, null)
    }
}
