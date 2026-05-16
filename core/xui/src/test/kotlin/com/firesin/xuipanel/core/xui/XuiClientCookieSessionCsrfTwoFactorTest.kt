package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.PanelLookup
import com.firesin.xuipanel.core.common.PanelMetadata
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpBus
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpRequest
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that [XuiClient.cookieSessionCsrf] (called indirectly via [XuiClient.fetchPanelSettings])
 * uses [TwoFactorOtpBus] on re-login when the panel has 2FA enabled.
 *
 * Two scenarios:
 * 1. Cold-start (no cached session) with 2FA panel — OTP is requested on initial login.
 * 2. Expired session (401 on CSRF) with 2FA panel — OTP is requested on re-login.
 */
class XuiClientCookieSessionCsrfTwoFactorTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var xuiClient: XuiClient

    private val tls = PanelTls(TlsMode.SYSTEM, null)

    private class FakePanelLookup(
        private val metaFor2fa: PanelMetadata = PanelMetadata("Panel A", twoFactorEnabled = true),
    ) : PanelLookup {
        override suspend fun lookup(panelId: String): PanelMetadata? =
            if (panelId == "2fa") metaFor2fa else null
    }

    private class FakeTwoFactorOtpBus(private val otpToReturn: String?) : TwoFactorOtpBus {
        override val requests: SharedFlow<TwoFactorOtpRequest> = MutableSharedFlow()
        var requestedPanelId: String? = null
        var requestedPanelName: String? = null

        override suspend fun requestOtp(panelId: String, panelName: String): String? {
            requestedPanelId = panelId
            requestedPanelName = panelName
            return otpToReturn
        }

        override fun completeRequest(requestId: String, otp: String?) = Unit
    }

    private val settingsBody = """{"success":true,"obj":{"webPort":2053}}"""
    private val csrfBody = """{"success":true,"obj":"csrf-token-abc"}"""

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
    }

    @Test
    fun `cookieSessionCsrf cold-start 2FA - OTP is requested before login`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = "123456")
        xuiClient = XuiClient(
            clientFactory, sessionCache,
            spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(),
            fakeBus, FakePanelLookup(),
        )
        // No cached session → cold-start login path
        coEvery { sessionCache.get(any()) } returns null

        val mockClient = buildMockClient { request ->
            when {
                request.url.encodedPath.endsWith("/csrf-token") -> 200 to csrfBody
                request.url.encodedPath.endsWith("/login") -> 200 to """{"success":true}"""
                else -> 200 to settingsBody
            }
        }
        coEvery { clientFactory.getClient(any(), any()) } returns mockClient

        val result = xuiClient.fetchPanelSettings("2fa", "https://panel.example.com", "admin", "secret", tls)

        assertInstanceOf(Result.Success::class.java, result)
        assert(fakeBus.requestedPanelId == "2fa") { "OTP bus should be called for 2FA panel" }
        assert(fakeBus.requestedPanelName == "Panel A")
    }

    @Test
    fun `cookieSessionCsrf expired session 2FA - OTP requested on re-login`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = "654321")
        xuiClient = XuiClient(
            clientFactory, sessionCache,
            spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(),
            fakeBus, FakePanelLookup(),
        )
        // Session appears cached, but CSRF returns 401 → triggers re-login
        coEvery { sessionCache.get(any()) } returns XuiSession("2fa")

        val csrfCallCount = AtomicInteger(0)
        val mockClient = buildMockClient { request ->
            when {
                request.url.encodedPath.endsWith("/csrf-token") -> {
                    val count = csrfCallCount.getAndIncrement()
                    if (count == 0) 401 to """{"msg":"unauthorized"}"""
                    else 200 to csrfBody
                }
                request.url.encodedPath.endsWith("/login") -> 200 to """{"success":true}"""
                else -> 200 to settingsBody
            }
        }
        coEvery { clientFactory.getClient(any(), any()) } returns mockClient

        val result = xuiClient.fetchPanelSettings("2fa", "https://panel.example.com", "admin", "secret", tls)

        assertInstanceOf(Result.Success::class.java, result)
        assert(fakeBus.requestedPanelId == "2fa") { "OTP bus should be called on session expiry for 2FA panel" }
    }

    @Test
    fun `cookieSessionCsrf non-2FA panel - OTP bus not consulted`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = "should-not-be-called")
        xuiClient = XuiClient(
            clientFactory, sessionCache,
            spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(),
            fakeBus, FakePanelLookup(),
        )
        coEvery { sessionCache.get(any()) } returns null

        val mockClient = buildMockClient { request ->
            when {
                request.url.encodedPath.endsWith("/csrf-token") -> 200 to csrfBody
                request.url.encodedPath.endsWith("/login") -> 200 to """{"success":true}"""
                else -> 200 to settingsBody
            }
        }
        coEvery { clientFactory.getClient(any(), any()) } returns mockClient

        // "other" panelId → FakePanelLookup returns null → non-2FA path
        val result = xuiClient.fetchPanelSettings("other", "https://panel.example.com", "admin", "secret", tls)

        assertInstanceOf(Result.Success::class.java, result)
        assert(fakeBus.requestedPanelId == null) { "OTP bus must NOT be called for non-2FA panel" }
    }

    // ---- helpers ----

    private fun buildMockClient(handler: (Request) -> Pair<Int, String>): OkHttpClient {
        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val (code, body) = handler(request)
            val fakeResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Unauthorized")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
            val mockCall = mockk<Call>()
            every { mockCall.execute() } returns fakeResponse
            every { mockCall.cancel() } just Runs
            every { mockCall.isExecuted() } returns false
            every { mockCall.isCanceled() } returns false
            every { mockCall.enqueue(any()) } answers {
                firstArg<Callback>().onResponse(mockCall, fakeResponse)
            }
            mockCall
        }
        return mockClient
    }
}
