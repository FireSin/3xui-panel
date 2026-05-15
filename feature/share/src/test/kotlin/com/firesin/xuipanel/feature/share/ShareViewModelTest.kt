package com.firesin.xuipanel.feature.share

import androidx.lifecycle.SavedStateHandle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.share.ShareError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
        // Default stub: server returns empty list → existing tests fall through to local builder.
        coEvery { xuiClient.fetchClientLinks(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(emptyList())
        // Default stub for panel settings — tests don't care, return an empty disabled config.
        coEvery { xuiClient.fetchPanelSettings(any(), any(), any(), any(), any()) } returns
            Result.Success(com.firesin.xuipanel.core.xui.dto.PanelSettingsDto())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakePanel(baseUrl: String = "https://panel.example.com:2053") = Panel(
        id = "p1",
        name = "Test Panel",
        baseUrl = baseUrl,
        login = "admin",
        password = "secret",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = true,
        createdAt = Instant.now(),
        lastLoginAt = null,
    )

    private fun fakeInbound(
        id: Int = 1,
        protocol: String = "vless",
        settings: String = vlessSettings(),
        streamSettings: String = tcpRealityStream(),
        port: Int = 443,
        remark: String = "vless-inbound",
    ) = InboundDto(
        id = id,
        remark = remark,
        port = port,
        protocol = protocol,
        enable = true,
        up = 0L,
        down = 0L,
        total = 0L,
        expiryTime = 0L,
        listen = "",
        settings = settings,
        streamSettings = streamSettings,
        tag = "inbound-$id",
        sniffing = "{}",
    )

    private fun savedState(inboundId: Int, clientKey: String) = SavedStateHandle(
        mapOf(
            ShareViewModel.ARG_INBOUND_ID to inboundId,
            ShareViewModel.ARG_CLIENT_KEY to clientKey,
        ),
    )

    private fun vlessSettings(uuid: String = TEST_UUID) = """
        {
          "clients": [
            {
              "id": "$uuid",
              "flow": "xtls-rprx-vision",
              "email": "user@test.com",
              "enable": true,
              "totalGB": 0,
              "expiryTime": 0,
              "limitIp": 0,
              "subId": "",
              "comment": "",
              "tgId": "",
              "reset": 0
            }
          ],
          "decryption": "none"
        }
    """.trimIndent()

    private fun vmessSettings(uuid: String = TEST_UUID) = """
        {
          "clients": [
            {
              "id": "$uuid",
              "email": "vmess@test.com",
              "enable": true,
              "totalGB": 0,
              "expiryTime": 0,
              "limitIp": 0,
              "subId": "",
              "comment": "",
              "tgId": "",
              "reset": 0
            }
          ]
        }
    """.trimIndent()

    /** Minimal TCP + Reality stream settings that ClientUri can parse successfully. */
    private fun tcpRealityStream() = """
        {
          "network": "tcp",
          "security": "reality",
          "realitySettings": {
            "serverNames": ["example.com"],
            "publicKey": "REDACTED_PBK",
            "shortIds": ["REDACTED_SID"],
            "settings": {
              "fingerprint": "chrome"
            }
          },
          "tcpSettings": {}
        }
    """.trimIndent()

    /** KCP stream — unsupported transport. */
    private fun kcpStream() = """
        {
          "network": "kcp",
          "security": "none"
        }
    """.trimIndent()

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `happy path vless reality produces Content state with non-blank uri`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        val content = state as ShareUiState.Content
        assert(content.uri.startsWith("vless://")) { "Expected vless URI, got: ${content.uri}" }
        assertEquals(TEST_UUID, (content.client as ClientConfig.Vless).id)
    }

    @Test
    fun `happy path vmess tcp none produces Content state`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(
            protocol = "vmess",
            settings = vmessSettings(),
            streamSettings = """{"network":"tcp","security":"none"}""",
        )
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        assert((state as ShareUiState.Content).uri.startsWith("vmess://"))
    }

    @Test
    fun `inbound not found by id produces Error state`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(id = 2)
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        // Request inboundId=99 which does not exist
        val vm = ShareViewModel(savedState(99, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Error::class.java, state)
        assertNotNull((state as ShareUiState.Error).shareError)
    }

    @Test
    fun `client not found by clientKey produces Error state`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        // Request a key that doesn't exist in the inbound
        val vm = ShareViewModel(savedState(1, "no-such-key"), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Error::class.java, state)
        assertNotNull((state as ShareUiState.Error).shareError)
    }

    @Test
    fun `unsupported transport kcp produces Error with UnsupportedTransport`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(streamSettings = kcpStream())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Error::class.java, state)
        assertInstanceOf(ShareError.UnsupportedTransport::class.java, (state as ShareUiState.Error).shareError)
    }

    @Test
    fun `domain error from fetchInbounds produces Error with domainError`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Error::class.java, state)
        assertEquals(DomainError.InvalidCredentials, (state as ShareUiState.Error).domainError)
    }

    @Test
    fun `no active panel produces Error state`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ShareUiState.Error::class.java, vm.uiState.value)
    }

    @Test
    fun `retry after error re-fetches and recovers to Content`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        // First call fails
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout"))) andThen
            Result.Success(listOf(inbound))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ShareUiState.Error::class.java, vm.uiState.value)

        vm.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
    }

    // ── getClientLinks server-first tests ─────────────────────────────────────

    @Test
    fun `server returns URL — Content uses server URL, local builder not called`() = runTest {
        val serverUrl = "vless://server-canonical-url@host:443?security=reality#remark"
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.fetchClientLinks(any(), any(), any(), any(), eq(1), eq("user@test.com")) } returns
            Result.Success(listOf(serverUrl))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        assertEquals(serverUrl, (state as ShareUiState.Content).uri)
    }

    @Test
    fun `server returns empty obj — Content uses local builder URL`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.fetchClientLinks(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(emptyList())

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        assert((state as ShareUiState.Content).uri.startsWith("vless://")) {
            "Expected local vless URI, got: ${state.uri}"
        }
    }

    @Test
    fun `server returns failure — Content uses local builder URL`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.fetchClientLinks(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        assert((state as ShareUiState.Content).uri.startsWith("vless://")) {
            "Expected local vless URI, got: ${state.uri}"
        }
    }

    @Test
    fun `client has no email — server not called, Content uses local builder URL`() = runTest {
        val settingsNoEmail = """
            {
              "clients": [
                {
                  "id": "$TEST_UUID",
                  "flow": "",
                  "email": "",
                  "enable": true,
                  "totalGB": 0,
                  "expiryTime": 0,
                  "limitIp": 0,
                  "subId": "",
                  "comment": "",
                  "tgId": "",
                  "reset": 0
                }
              ],
              "decryption": "none"
            }
        """.trimIndent()
        val panel = fakePanel()
        val inbound = fakeInbound(settings = settingsNoEmail)
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ShareViewModel(savedState(1, TEST_UUID), repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ShareUiState.Content::class.java, state)
        assert((state as ShareUiState.Content).uri.startsWith("vless://")) {
            "Expected local vless URI, got: ${state.uri}"
        }
        // Verify server was NOT called with a blank email
        io.mockk.coVerify(exactly = 0) {
            xuiClient.fetchClientLinks(any(), any(), any(), any(), any(), eq(""))
        }
    }

    private companion object {
        const val TEST_UUID = "aaaabbbb-cccc-dddd-eeee-ffffffffffff"
    }
}
