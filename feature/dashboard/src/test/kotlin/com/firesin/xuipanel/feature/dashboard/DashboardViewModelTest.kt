package com.firesin.xuipanel.feature.dashboard

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.MemDto
import com.firesin.xuipanel.core.xui.dto.NetIoDto
import com.firesin.xuipanel.core.xui.dto.NetTrafficDto
import com.firesin.xuipanel.core.xui.dto.PublicIpDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.XrayStatusDto
import com.firesin.xuipanel.feature.dashboard.ui.DashboardUiState
import com.firesin.xuipanel.feature.dashboard.ui.DashboardViewModel
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no active panel produces NoActivePanel state`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = DashboardViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(DashboardUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    @Test
    fun `active panel triggers fetchServerStatus and emits Content`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchServerStatus(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeStatus())

        val vm = DashboardViewModel(repository, xuiClient)

        vm.uiState.test {
            // Initial NoActivePanel emission
            skipItems(1)
            testDispatcher.scheduler.advanceUntilIdle()
            // Loading then Content (or just Content if scheduler collapses emissions)
            val state = expectMostRecentItem()
            assertInstanceOf(DashboardUiState.Content::class.java, state)
            assertEquals(panel.id, (state as DashboardUiState.Content).panel.id)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            xuiClient.fetchServerStatus(panel.id, panel.baseUrl, panel.login, panel.password, panel.trustSelfSigned)
        }
    }

    @Test
    fun `fetch failure emits Error state`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchServerStatus(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = DashboardViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(DashboardUiState.Error::class.java, state)
        assertEquals(DomainError.InvalidCredentials, (state as DashboardUiState.Error).error)
    }

    @Test
    fun `refresh re-fetches status`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchServerStatus(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeStatus())

        val vm = DashboardViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) {
            xuiClient.fetchServerStatus(panel.id, any(), any(), any(), any())
        }
    }

    private fun fakePanel() = Panel(
        id = "p1",
        name = "Test",
        baseUrl = "https://example.com",
        login = "admin",
        password = "secret",
        trustSelfSigned = false,
        isActive = true,
        createdAt = Instant.now(),
        lastLoginAt = null,
    )

    private fun fakeStatus() = ServerStatusDto(
        cpu = 10.0,
        mem = MemDto(current = 100, total = 1000),
        xray = XrayStatusDto(state = "running", errorMsg = "", version = "1.0"),
        uptime = 3600,
        loads = listOf(0.1, 0.2, 0.3),
        tcpCount = 0,
        udpCount = 0,
        netIO = NetIoDto(up = 0, down = 0),
        netTraffic = NetTrafficDto(sent = 0, recv = 0),
        publicIP = PublicIpDto(ipv4 = "1.2.3.4", ipv6 = ""),
        appStats = null,
    )
}
