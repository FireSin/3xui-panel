package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelAuth
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.NoOpPanelLookup
import com.firesin.xuipanel.core.common.twofactor.NoOpTwoFactorOtpBus
import com.firesin.xuipanel.core.network.CsrfTokenStore
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoDto
import com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoObj
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

/**
 * Tests for Bundle A (fetchXrayVersion, fetchPanelUpdateInfo) and
 * Bundle B (importDb, updateBuiltinGeofile, fetchConfigJson) methods in [XuiClient].
 *
 * Uses the OkHttp mock approach from [XuiClientErrorMappingTest]:
 * mock [OkHttpClient.newCall] to return a canned [Response] so that Retrofit
 * and the Kotlin-serialization converter run for real.
 */
class XuiClientServerInfoTest {

    private lateinit var clientFactory: OkHttpClientFactory
    private lateinit var sessionCache: XuiSessionCache
    private lateinit var xuiClient: XuiClient

    private val auth = PanelAuth.Login("user", "pass")
    private val tls = PanelTls(TlsMode.SYSTEM, null)

    @BeforeEach
    fun setUp() {
        clientFactory = mockk(relaxed = true)
        sessionCache = mockk(relaxed = true)
        xuiClient = XuiClient(clientFactory, sessionCache, CsrfTokenStore(), spyk(PinMismatchEventDispatcher()), WsUiEventDispatcher(), NoOpTwoFactorOtpBus, NoOpPanelLookup)
        coEvery { sessionCache.get(any()) } returns XuiSession("p1")
    }

    // ---- fetchXrayVersion (now reads serverStatus.obj.xray.version) ----

    private val serverStatusBody = """
        {"success":true,"obj":{"cpu":1.0,"cpuCores":1,"logicalPro":1,"cpuSpeedMhz":3000.0,
        "mem":{"current":100,"total":200},"swap":{"current":0,"total":0},
        "disk":{"current":0,"total":0},
        "xray":{"state":"running","errorMsg":"","version":"26.4.25"},
        "uptime":1,"loads":[0,0,0],"tcpCount":0,"udpCount":0,
        "netIO":{"up":0,"down":0},"netTraffic":{"sent":0,"recv":0},
        "publicIP":{"ipv4":"1.1.1.1","ipv6":"N/A"},
        "appStats":{"threads":1,"mem":1,"uptime":1}}}
    """.trimIndent()

    @Test
    fun `fetchXrayVersion - success returns version from serverStatus`() = runTest {
        mockResponse(url = "/panel/api/server/status", body = serverStatusBody)

        val result = xuiClient.fetchXrayVersion("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        assertEquals("26.4.25", (result as Result.Success).data)
    }

    @Test
    fun `fetchXrayVersion - success=false returns PanelResponse error`() = runTest {
        mockResponse(
            url = "/panel/api/server/status",
            body = """{"success":false,"msg":"not supported"}""",
        )

        val result = xuiClient.fetchXrayVersion("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.PanelResponse)
    }

    @Test
    fun `fetchXrayVersion - network error maps to DomainError Network`() = runTest {
        mockThrow(java.io.IOException("timeout"))

        val result = xuiClient.fetchXrayVersion("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.Network)
    }

    // ---- fetchAvailableXrayVersions ----

    @Test
    fun `fetchAvailableXrayVersions - success returns list`() = runTest {
        mockResponse(
            url = "/panel/api/server/getXrayVersion",
            body = """{"success":true,"obj":["v26.5.9","v26.4.25"]}""",
        )

        val result = xuiClient.fetchAvailableXrayVersions("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        assertEquals(listOf("v26.5.9", "v26.4.25"), (result as Result.Success).data)
    }

    @Test
    fun `fetchAvailableXrayVersions - null obj treated as empty list`() = runTest {
        mockResponse(
            url = "/panel/api/server/getXrayVersion",
            body = """{"success":true}""",
        )

        val result = xuiClient.fetchAvailableXrayVersions("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    // ---- fetchPanelUpdateInfo ----

    @Test
    fun `fetchPanelUpdateInfo - returns info with isUpdatable=true`() = runTest {
        mockResponse(
            url = "/panel/api/server/getPanelUpdateInfo",
            body = """{"success":true,"obj":{"currentVersion":"2.3.12","latestVersion":"2.3.14","isUpdatable":true}}""",
        )

        val result = xuiClient.fetchPanelUpdateInfo("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success).data
        assertTrue(info.isUpdatable)
        assertEquals("2.3.14", info.latestVersion)
        assertEquals("2.3.12", info.currentVersion)
    }

    @Test
    fun `fetchPanelUpdateInfo - isUpdatable=false when up-to-date`() = runTest {
        mockResponse(
            url = "/panel/api/server/getPanelUpdateInfo",
            body = """{"success":true,"obj":{"currentVersion":"2.3.14","latestVersion":"2.3.14","isUpdatable":false}}""",
        )

        val result = xuiClient.fetchPanelUpdateInfo("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        val info = (result as Result.Success).data
        assertTrue(!info.isUpdatable)
    }

    // ---- updateBuiltinGeofile ----

    @Test
    fun `updateBuiltinGeofile - success returns Unit`() = runTest {
        mockResponse(
            url = "/panel/api/server/updateGeofile",
            body = """{"success":true,"msg":"done"}""",
        )

        val result = xuiClient.updateBuiltinGeofile("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `updateBuiltinGeofile - success=false returns PanelResponse error`() = runTest {
        mockResponse(
            url = "/panel/api/server/updateGeofile",
            body = """{"success":false,"msg":"download failed"}""",
        )

        val result = xuiClient.updateBuiltinGeofile("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
        assertTrue((result as Result.Failure).error is DomainError.PanelResponse)
    }

    // ---- fetchConfigJson ----

    @Test
    fun `fetchConfigJson - returns pretty-printed JSON string`() = runTest {
        mockResponse(
            url = "/panel/api/server/getConfigJson",
            body = """{"success":true,"obj":{"log":{"loglevel":"warning"}}}""",
        )

        val result = xuiClient.fetchConfigJson("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Success::class.java, result)
        val json = (result as Result.Success).data
        assertTrue(json.contains("loglevel"))
        assertTrue(json.contains("warning"))
    }

    @Test
    fun `fetchConfigJson - null obj returns PanelResponse error`() = runTest {
        mockResponse(
            url = "/panel/api/server/getConfigJson",
            body = """{"success":false,"msg":"unavailable"}""",
        )

        val result = xuiClient.fetchConfigJson("p1", BASE_URL, auth, tls)

        assertInstanceOf(Result.Failure::class.java, result)
    }

    // ---- importDb ----

    @Test
    fun `importDb - success returns Unit`() = runTest {
        mockResponse(
            url = "/panel/api/server/importDB",
            body = """{"success":true}""",
        )

        val result = xuiClient.importDb(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            fileBytes = byteArrayOf(1, 2, 3),
            fileName = "x-ui.db",
        )

        assertInstanceOf(Result.Success::class.java, result)
    }

    @Test
    fun `importDb - EOFException treated as success (panel restart)`() = runTest {
        mockThrow(java.io.EOFException("connection closed"))

        val result = xuiClient.importDb(
            panelId = "p1",
            baseUrl = BASE_URL,
            auth = auth,
            tls = tls,
            fileBytes = byteArrayOf(1, 2, 3),
            fileName = "x-ui.db",
        )

        // Panel restarted before writing response — should be treated as success
        assertInstanceOf(Result.Success::class.java, result)
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

        coEvery { clientFactory.getClient(any(), any(), any()) } returns mockClient
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

        coEvery { clientFactory.getClient(any(), any(), any()) } returns mockClient
    }

    private companion object {
        const val BASE_URL = "https://panel.example.com"
    }
}
