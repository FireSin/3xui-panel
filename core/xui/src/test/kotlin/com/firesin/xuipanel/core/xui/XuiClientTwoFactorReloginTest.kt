package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.NoOpPanelLookup
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpBus
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpRequest
import com.firesin.xuipanel.core.network.CsrfTokenStore
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that [XuiClient] correctly uses [TwoFactorOtpBus] during re-authentication
 * after a 401 when 2FA is enabled.
 */
class XuiClientTwoFactorReloginTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var xuiClient: XuiClient

    private val tls = PanelTls(TlsMode.SYSTEM, null)

    /**
     * Fake bus that immediately returns a fixed OTP string (or null to simulate cancel).
     */
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

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
    }

    @Test
    fun `2FA relogin - OTP is requested and login succeeds`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = "123456")
        xuiClient = XuiClient(clientFactory, sessionCache, CsrfTokenStore(), spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(), fakeBus, NoOpPanelLookup)

        // No cached session → first login call returns 200 (login succeeds)
        // Then the actual API call returns 401 → triggers re-auth with OTP → login again → retry returns 200
        val callCount = AtomicInteger(0)

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val count = callCount.getAndIncrement()

            val (code, body) = when {
                request.url.encodedPath.endsWith("/csrf-token") ->
                    200 to """{"success":true,"obj":"csrf"}"""
                request.url.encodedPath.endsWith("/login") ->
                    200 to """{"success":true}"""
                count == 2 -> // first real API call after initial login → 401
                    401 to """{"msg":"session expired"}"""
                else -> // retry after re-login
                    200 to """{"success":true,"obj":[]}"""
            }

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
        coEvery { clientFactory.getClient(any(), any(), any()) } returns mockClient
        // Simulate no cached session to force initial login
        coEvery { sessionCache.get(any()) } returns null

        val auth = PanelAuth.Login(
            username = "admin",
            password = "secret",
            twoFactorEnabled = true,
            panelName = "Test Panel",
        )
        val result = xuiClient.fetchInbounds("p1", "https://panel.example.com", auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        assert(fakeBus.requestedPanelId == "p1")
        assert(fakeBus.requestedPanelName == "Test Panel")
    }

    @Test
    fun `2FA relogin - user cancels OTP throws XuiAuthException mapped to InvalidCredentials`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = null)
        xuiClient = XuiClient(clientFactory, sessionCache, CsrfTokenStore(), spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(), fakeBus, NoOpPanelLookup)

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val (code, body) = when {
                request.url.encodedPath.endsWith("/csrf-token") -> 200 to """{"success":true,"obj":"csrf"}"""
                request.url.encodedPath.endsWith("/login") -> 200 to """{"success":true}"""
                else -> 401 to """{"msg":"unauthorized"}"""
            }
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
        coEvery { clientFactory.getClient(any(), any(), any()) } returns mockClient
        coEvery { sessionCache.get(any()) } returns null

        val auth = PanelAuth.Login(
            username = "admin",
            password = "secret",
            twoFactorEnabled = true,
            panelName = "Test Panel",
        )
        val result = xuiClient.fetchInbounds("p1", "https://panel.example.com", auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertInstanceOf(
            com.firesin.xuipanel.core.common.DomainError.InvalidCredentials::class.java,
            (result as Result.Failure).error,
        )
    }

    @Test
    fun `non-2FA relogin - OTP bus is NOT consulted on 401`() = runTest {
        val fakeBus = FakeTwoFactorOtpBus(otpToReturn = "should-not-be-called")
        xuiClient = XuiClient(clientFactory, sessionCache, CsrfTokenStore(), spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(), fakeBus, NoOpPanelLookup)

        val callCount = AtomicInteger(0)
        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val count = callCount.getAndIncrement()
            val (code, body) = when {
                request.url.encodedPath.endsWith("/csrf-token") -> 200 to """{"success":true,"obj":"csrf"}"""
                request.url.encodedPath.endsWith("/login") -> 200 to """{"success":true}"""
                count == 2 -> 401 to """{"msg":"expired"}"""
                else -> 200 to """{"success":true,"obj":[]}"""
            }
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
        coEvery { clientFactory.getClient(any(), any(), any()) } returns mockClient
        coEvery { sessionCache.get(any()) } returns null

        val auth = PanelAuth.Login(
            username = "admin",
            password = "secret",
            twoFactorEnabled = false,
        )
        val result = xuiClient.fetchInbounds("p1", "https://panel.example.com", auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        // Bus was not consulted
        assert(fakeBus.requestedPanelId == null) { "OTP bus should not be called for non-2FA panel" }
    }
}
