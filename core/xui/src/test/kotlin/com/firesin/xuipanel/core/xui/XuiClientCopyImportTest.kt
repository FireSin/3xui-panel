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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.io.IOException

/**
 * Tests for [XuiClient.copyClients] and [XuiClient.importInbounds].
 * Mirrors the OkHttp mock approach from [XuiClientServerInfoTest].
 */
class XuiClientCopyImportTest {

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

    // ---- copyClients ----

    @Test
    fun `copyClients - success returns Unit`() = runTest {
        mockResponse(body = """{"success":true,"msg":"ok"}""")

        val result = xuiClient.copyClients(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            targetInboundId = 3,
            sourceInboundId = 7,
            clientEmails = emptyList(),
            flow = null,
        )

        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `copyClients with selected emails and flow - success`() = runTest {
        mockResponse(body = """{"success":true}""")

        val result = xuiClient.copyClients(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            targetInboundId = 1,
            sourceInboundId = 2,
            clientEmails = listOf("alice", "bob"),
            flow = "xtls-rprx-vision",
        )

        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `copyClients - success=false returns PanelResponse error`() = runTest {
        mockResponse(body = """{"success":false,"msg":"source not found"}""")

        val result = xuiClient.copyClients(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            targetInboundId = 3,
            sourceInboundId = 99,
            clientEmails = emptyList(),
            flow = null,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.PanelResponse)
    }

    @Test
    fun `copyClients - network error maps to DomainError Network`() = runTest {
        mockThrow(IOException("timeout"))

        val result = xuiClient.copyClients(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            targetInboundId = 1,
            sourceInboundId = 2,
            clientEmails = emptyList(),
            flow = null,
        )

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.Network)
    }

    // ---- importInbounds ----

    @Test
    fun `importInbounds - success returns Unit`() = runTest {
        mockResponse(body = """{"success":true,"msg":"imported"}""")

        val result = xuiClient.importInbounds(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            jsonText = """{"remark":"test","port":443}""",
        )

        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `importInbounds - success=false returns PanelResponse error`() = runTest {
        mockResponse(body = """{"success":false,"msg":"invalid json"}""")

        val result = xuiClient.importInbounds(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            jsonText = "not valid",
        )

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.PanelResponse)
    }

    @Test
    fun `importInbounds - EOFException treated as success (xray restart)`() = runTest {
        mockThrow(EOFException("connection closed"))

        val result = xuiClient.importInbounds(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            jsonText = """{"remark":"test"}""",
        )

        // EOF tolerance: panel may restart Xray after import
        assertInstanceOf(Result.Success::class.java, result)
    }

    // ---- helpers ----

    private fun mockResponse(body: String, code: Int = 200) {
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("$BASE_URL/panel/api/inbounds/import").build())
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
