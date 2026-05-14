package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class XuiClientCustomGeoAliasesTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var xuiClient: XuiClient

    private val auth = PanelAuth.Login("user", "pass")
    private val tls = PanelTls(TlsMode.SYSTEM, null)

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
        xuiClient = XuiClient(clientFactory, sessionCache, spyk(PinMismatchEventDispatcher()))
        coEvery { sessionCache.get(any()) } returns XuiSession("p1")
    }

    @Test
    fun `fetchCustomGeoAliases - success returns list of aliases`() = runTest {
        mockResponse(
            url = "/panel/api/custom-geo/aliases",
            body = """{"success":true,"obj":["geoip:cn","geoip:private","geosite:google","geoip:myips"]}""",
        )

        val result = xuiClient.fetchCustomGeoAliases("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        val aliases = (result as Result.Success).data
        assertEquals(4, aliases.size)
        assertEquals("geoip:cn", aliases[0])
        assertEquals("geosite:google", aliases[2])
    }

    @Test
    fun `fetchCustomGeoAliases - empty obj returns empty list`() = runTest {
        mockResponse(
            url = "/panel/api/custom-geo/aliases",
            body = """{"success":true,"obj":[]}""",
        )

        val result = xuiClient.fetchCustomGeoAliases("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `fetchCustomGeoAliases - success=false returns PanelResponse error`() = runTest {
        mockResponse(
            url = "/panel/api/custom-geo/aliases",
            body = """{"success":false,"msg":"not available"}""",
        )

        val result = xuiClient.fetchCustomGeoAliases("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.PanelResponse)
    }

    @Test
    fun `fetchCustomGeoAliases - network error maps to DomainError Network`() = runTest {
        mockThrow(IOException("connection reset"))

        val result = xuiClient.fetchCustomGeoAliases("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.Network)
    }

    // ---- helpers ----

    private fun mockResponse(url: String, body: String, code: Int = 200) {
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("$BASE_URL$url").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
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

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any<Request>()) } returns mockCall
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()

        coEvery { clientFactory.getClient(any(), any()) } returns mockClient
    }

    private fun mockThrow(exception: Throwable) {
        val mockCall = mockk<Call>()
        every { mockCall.execute() } throws exception
        every { mockCall.cancel() } just Runs
        every { mockCall.isExecuted() } returns false
        every { mockCall.isCanceled() } returns false
        every { mockCall.enqueue(any()) } answers {
            firstArg<Callback>().onFailure(mockCall, exception as? IOException ?: IOException(exception))
        }

        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any<Request>()) } returns mockCall
        every { mockClient.newBuilder() } returns OkHttpClient.Builder()

        coEvery { clientFactory.getClient(any(), any()) } returns mockClient
    }

    private companion object {
        const val BASE_URL = "https://panel.example.com"
    }
}
