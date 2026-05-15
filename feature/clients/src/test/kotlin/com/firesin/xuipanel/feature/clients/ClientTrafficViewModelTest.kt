package com.firesin.xuipanel.feature.clients

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.WsNotification
import com.firesin.xuipanel.core.common.WsUiEventBus
import com.firesin.xuipanel.core.data.model.Panel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientTrafficDto
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClientTrafficViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient
    private val wsBus: WsUiEventBus = object : WsUiEventBus {
        override val notifications = MutableSharedFlow<WsNotification>().asSharedFlow()
        override val invalidations = MutableSharedFlow<String>().asSharedFlow()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
        // Default stubs for background calls made on panel load
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any()) } returns Result.Success(emptySet())
        coEvery { xuiClient.fetchLastOnline(any(), any(), any(), any()) } returns Result.Success(emptyMap())
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadClientTraffic transitions Loading then Loaded on success`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.fetchClientTrafficsByEmail(any(), any(), any(), any(), any())
        } returns Result.Success(fakeTrafficDto("alice@test.com"))

        val vm = ClientsViewModel(repository, xuiClient, wsBus)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.trafficState.test {
            skipItems(1) // Idle initial value

            vm.loadClientTraffic("alice@test.com")
            assertInstanceOf(ClientsViewModel.TrafficState.Loading::class.java, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()
            val loaded = awaitItem()
            assertInstanceOf(ClientsViewModel.TrafficState.Loaded::class.java, loaded)
            val traffic = (loaded as ClientsViewModel.TrafficState.Loaded).traffic
            assert(traffic.email == "alice@test.com")
            assert(traffic.up == 1_000L)
            assert(traffic.down == 2_000L)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadClientTraffic transitions to NoData on failure`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.fetchClientTrafficsByEmail(any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.PanelResponse(0, "email not found"))

        val vm = ClientsViewModel(repository, xuiClient, wsBus)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.trafficState.test {
            skipItems(1) // Idle

            vm.loadClientTraffic("ghost@test.com")
            assertInstanceOf(ClientsViewModel.TrafficState.Loading::class.java, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(ClientsViewModel.TrafficState.NoData::class.java, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadClientTraffic does nothing when email is blank`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)

        val vm = ClientsViewModel(repository, xuiClient, wsBus)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.loadClientTraffic("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ClientsViewModel.TrafficState.Idle::class.java, vm.trafficState.value)
        coVerify(exactly = 0) {
            xuiClient.fetchClientTrafficsByEmail(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `resetTrafficState returns to Idle`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.fetchClientTrafficsByEmail(any(), any(), any(), any(), any())
        } returns Result.Success(fakeTrafficDto("alice@test.com"))

        val vm = ClientsViewModel(repository, xuiClient, wsBus)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.loadClientTraffic("alice@test.com")
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ClientsViewModel.TrafficState.Loaded::class.java, vm.trafficState.value)

        vm.resetTrafficState()
        assertInstanceOf(ClientsViewModel.TrafficState.Idle::class.java, vm.trafficState.value)
    }

    @Test
    fun `loadClientTraffic does nothing when no active panel`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = ClientsViewModel(repository, xuiClient, wsBus)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(ClientsUiState.NoActivePanel::class.java, vm.uiState.value)

        vm.loadClientTraffic("alice@test.com")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            xuiClient.fetchClientTrafficsByEmail(any(), any(), any(), any(), any())
        }
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
        settings: String = "{\"clients\":[]}",
    ) = InboundDto(
        id = id,
        remark = "test-$id",
        port = 443,
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

    private fun fakeTrafficDto(email: String) = ClientTrafficDto(
        id = 1,
        inboundId = 1,
        enable = true,
        email = email,
        up = 1_000L,
        down = 2_000L,
        expiryTime = 0L,
        total = 0L,
        reset = 0L,
    )
}
