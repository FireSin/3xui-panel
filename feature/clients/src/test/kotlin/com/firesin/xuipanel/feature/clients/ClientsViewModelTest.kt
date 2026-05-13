package com.firesin.xuipanel.feature.clients

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.clients.ui.ClientsUiState
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any()) } returns
            Result.Success(emptySet())
        coEvery { xuiClient.fetchLastOnline(any(), any(), any(), any()) } returns
            Result.Success(emptyMap())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no active panel produces NoActivePanel state`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ClientsUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    @Test
    fun `active panel triggers fetch and emits Content with parsed clients`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ClientsViewModel(repository, xuiClient)

        vm.uiState.test {
            skipItems(1) // initial NoActivePanel
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(ClientsUiState.Content::class.java, state)
            val content = state as ClientsUiState.Content
            assertEquals(panel.id, content.panel.id)
            assertEquals(1, content.inbounds.size)
            assertEquals(inbound.id, content.selectedInboundId)
            assertEquals(1, content.clients.size)
            assertInstanceOf(ClientConfig.Vmess::class.java, content.clients[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fetch failure emits Error state`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ClientsUiState.Error::class.java, state)
        assertEquals(DomainError.InvalidCredentials, (state as ClientsUiState.Error).error)
    }

    @Test
    fun `addClient calls xuiClient then refetches`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.addClient(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val newClient = fakeVmessClient("new@test.com")
        vm.addClient(inbound.id, newClient)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.addClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inbound.id,
                client = newClient,
            )
        }
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(any(), any(), any(), any()) }
    }

    @Test
    fun `addClient failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.addClient(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.addClient(inbound.id, fakeVmessClient("x@test.com"))
            testDispatcher.scheduler.advanceUntilIdle()
            val error = awaitItem()
            assertInstanceOf(DomainError.Network::class.java, error)
            cancelAndIgnoreRemainingEvents()
        }

        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `updateClient calls xuiClient then refetches`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.updateClient(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedClient = fakeVmessClient("updated@test.com")
        val key = "11111111-2222-3333-4444-555555555555"
        vm.updateClient(inbound.id, key, updatedClient)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.updateClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inbound.id,
                clientKey = key,
                client = updatedClient,
            )
        }
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(any(), any(), any(), any()) }
    }

    @Test
    fun `updateClient failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.updateClient(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.updateClient(inbound.id, "key", fakeVmessClient("u@test.com"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(DomainError.InvalidCredentials::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteClient calls xuiClient with urlKey then refetches`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.deleteClient(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val client = fakeVmessClient("del@test.com")
        vm.deleteClient(inbound.id, client)
        testDispatcher.scheduler.advanceUntilIdle()

        // urlKey for vmess = id
        coVerify(exactly = 1) {
            xuiClient.deleteClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inbound.id,
                clientKey = client.id,
            )
        }
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(any(), any(), any(), any()) }
    }

    @Test
    fun `deleteClient failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.deleteClient(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("err")))

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.deleteClient(inbound.id, fakeVmessClient("d@test.com"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(DomainError.Network::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetTraffic calls xuiClient with email then refetches`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.resetClientTraffic(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val client = fakeVmessClient("reset@test.com")
        vm.resetTraffic(inbound.id, client)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.resetClientTraffic(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inbound.id,
                email = client.email,
            )
        }
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(any(), any(), any(), any()) }
    }

    @Test
    fun `resetTraffic failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.resetClientTraffic(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.resetTraffic(inbound.id, fakeVmessClient("r@test.com"))
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(DomainError.Network::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectInbound updates clients list`() = runTest {
        val panel = fakePanel()
        val inbound1 = fakeInbound(id = 1, protocol = "vmess", settings = vmessSettings())
        val inbound2 = fakeInbound(id = 2, protocol = "vless", settings = vlessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound1, inbound2))

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial selection is inbound1 (first)
        val initial = vm.uiState.value as ClientsUiState.Content
        assertEquals(1, initial.selectedInboundId)
        assertEquals(1, initial.clients.size)
        assertInstanceOf(ClientConfig.Vmess::class.java, initial.clients[0])

        // Switch to inbound2
        vm.selectInbound(2)
        val updated = vm.uiState.value as ClientsUiState.Content
        assertEquals(2, updated.selectedInboundId)
        assertEquals(1, updated.clients.size)
        assertInstanceOf(ClientConfig.Vless::class.java, updated.clients[0])
    }

    @Test
    fun `unsupported protocol inbound has empty clients list`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(id = 1, protocol = "trojan", settings = "{\"clients\":[]}")
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val content = vm.uiState.value as ClientsUiState.Content
        assertEquals(emptyList<ClientConfig>(), content.clients)
    }

    @Test
    fun `errorShown clears errorMessage`() = runTest {
        val panel = fakePanel()
        val inbound = fakeInbound(protocol = "vmess", settings = vmessSettings())
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(inbound))
        coEvery { xuiClient.addClient(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = ClientsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addClient(inbound.id, fakeVmessClient("e@test.com"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DomainError.InvalidCredentials, vm.errorMessage.value)
        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

    // --- Helpers ---

    private fun fakePanel() = Panel(
        id = "p1",
        name = "Test Panel",
        baseUrl = "https://example.com",
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
        protocol: String = "vmess",
        settings: String = "{}",
    ) = InboundDto(
        id = id,
        remark = "test-$id",
        port = 443 + id,
        protocol = protocol,
        enable = true,
        up = 0L,
        down = 0L,
        total = 0L,
        expiryTime = 0L,
        listen = "",
        settings = settings,
        streamSettings = "{}",
        tag = "inbound-$id",
        sniffing = "{}",
    )

    private fun fakeVmessClient(email: String) = ClientConfig.Vmess(
        id = "11111111-2222-3333-4444-555555555555",
        email = email,
        enable = true,
        totalGB = 0L,
        expiryTime = 0L,
        limitIp = 0,
        subId = "",
        comment = "",
    )

    private fun vmessSettings() = """
        {
          "clients": [
            {
              "id": "11111111-2222-3333-4444-555555555555",
              "email": "existing@test.com",
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

    private fun vlessSettings() = """
        {
          "clients": [
            {
              "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "flow": "",
              "email": "vless@test.com",
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
}
