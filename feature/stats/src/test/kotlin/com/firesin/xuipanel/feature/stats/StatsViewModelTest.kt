package com.firesin.xuipanel.feature.stats

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.core.xui.dto.InboundDto
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var panelRepository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        panelRepository = mockk()
        xuiClient = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ────────── happy path ──────────

    @Test
    fun `happy path - both APIs ok - emits Content with correct summary`() = runTest {
        val panel = fakePanel()
        val inbounds = listOf(
            fakeInbound(id = 1, up = 100L, down = 200L, clients = listOf(
                fakeClient(id = 1, enable = true),
                fakeClient(id = 2, enable = false),
            )),
            fakeInbound(id = 2, up = 50L, down = 70L, clients = listOf(
                fakeClient(id = 3, enable = true),
            )),
        )
        val onlineEmails = setOf("a@b.com")

        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) } returns
            Result.Success(inbounds)
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any(), any()) } returns
            Result.Success(onlineEmails)

        val vm = StatsViewModel(panelRepository, xuiClient)

        vm.uiState.test {
            skipItems(1) // initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(StatsUiState.Content::class.java, state)
            state as StatsUiState.Content

            assertEquals(150L, state.summary.totalUp)
            assertEquals(270L, state.summary.totalDown)
            assertEquals(2, state.summary.inboundCount)
            assertEquals(2, state.summary.activeClientCount) // clients id=1 and id=3 are enabled
            assertEquals(onlineEmails, state.onlineEmails)
            assertTrue(state.onlinesAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ────────── inbounds failure ──────────

    @Test
    fun `inbounds fail - emits Error`() = runTest {
        val panel = fakePanel()
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any(), any()) } returns
            Result.Success(emptySet())

        val vm = StatsViewModel(panelRepository, xuiClient)

        vm.uiState.test {
            skipItems(1) // initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(StatsUiState.Error::class.java, state)
            assertEquals(DomainError.InvalidCredentials, (state as StatsUiState.Error).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ────────── onlines failure ──────────

    @Test
    fun `onlines fail, inbounds ok - Content with onlinesAvailable=false`() = runTest {
        val panel = fakePanel()
        val inbounds = listOf(fakeInbound(id = 1))
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) } returns
            Result.Success(inbounds)
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = StatsViewModel(panelRepository, xuiClient)

        vm.uiState.test {
            skipItems(1) // initial Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(StatsUiState.Content::class.java, state)
            state as StatsUiState.Content
            assertFalse(state.onlinesAvailable)
            assertTrue(state.onlineEmails.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ────────── no active panel ──────────

    @Test
    fun `no active panel - emits NoActivePanel`() = runTest {
        every { panelRepository.observeActive() } returns flowOf(null)

        val vm = StatsViewModel(panelRepository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(StatsUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    // ────────── toggleExpanded ──────────

    @Test
    fun `toggleExpanded(7) adds and removes inboundId from expandedIds`() = runTest {
        val panel = fakePanel()
        val inbounds = listOf(fakeInbound(id = 7))
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) } returns
            Result.Success(inbounds)
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any(), any()) } returns
            Result.Success(emptySet())

        val vm = StatsViewModel(panelRepository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially not expanded
        val before = vm.uiState.value as StatsUiState.Content
        assertFalse(7 in before.expandedIds)

        // Toggle in
        vm.toggleExpanded(7)
        val afterAdd = vm.uiState.value as StatsUiState.Content
        assertTrue(7 in afterAdd.expandedIds)

        // Toggle out
        vm.toggleExpanded(7)
        val afterRemove = vm.uiState.value as StatsUiState.Content
        assertFalse(7 in afterRemove.expandedIds)
    }

    // ────────── refresh preserves expandedIds ──────────

    @Test
    fun `refresh preserves expandedIds from current Content`() = runTest {
        val panel = fakePanel()
        val inbounds = listOf(fakeInbound(id = 5))
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) } returns
            Result.Success(inbounds)
        coEvery { xuiClient.fetchOnlines(any(), any(), any(), any(), any()) } returns
            Result.Success(emptySet())

        val vm = StatsViewModel(panelRepository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        // Expand inbound 5
        vm.toggleExpanded(5)
        assertTrue(5 in (vm.uiState.value as StatsUiState.Content).expandedIds)

        // Refresh
        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // expandedIds still contains 5
        val afterRefresh = vm.uiState.value as StatsUiState.Content
        assertTrue(5 in afterRefresh.expandedIds)
    }

    // ────────── helpers ──────────

    private fun fakePanel() = Panel(
        id = "p1",
        name = "Test",
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
        id: Int,
        up: Long = 0L,
        down: Long = 0L,
        clients: List<ClientStatDto> = emptyList(),
    ) = InboundDto(
        id = id,
        up = up,
        down = down,
        total = 0L,
        remark = "inbound-$id",
        enable = true,
        expiryTime = 0L,
        clientStats = clients,
        listen = "",
        port = 443,
        protocol = "vmess",
        settings = "{}",
        streamSettings = "{}",
        tag = "tag-$id",
        sniffing = "{}",
    )

    private fun fakeClient(id: Int, enable: Boolean = true) = ClientStatDto(
        id = id,
        inboundId = 1,
        enable = enable,
        email = "client$id@test.com",
        up = 0L,
        down = 0L,
        expiryTime = 0L,
        total = 0L,
        reset = 0L,
    )
}
