package com.firesin.xuipanel.feature.stats

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import com.firesin.xuipanel.core.xui.XuiClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelChartTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var panelRepository: PanelRepository
    private lateinit var xuiClient: XuiClient
    private lateinit var historyRepository: TrafficHistoryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        panelRepository = mockk()
        xuiClient = mockk()
        historyRepository = mockk()
        every { panelRepository.observeActive() } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ────────── range state ──────────

    @Test
    fun `initial range is D7`() {
        val vm = createVm()
        assertEquals(ChartRange.D7, vm.range.value)
    }

    @Test
    fun `setRange updates range state`() {
        val vm = createVm()
        vm.setRange(ChartRange.D30)
        assertEquals(ChartRange.D30, vm.range.value)
        vm.setRange(ChartRange.D90)
        assertEquals(ChartRange.D90, vm.range.value)
    }

    // ────────── chartFlow fromEpoch calculation ──────────

    @Test
    fun `chartFlow D7 uses fromEpoch = today - 7 days`() = runTest {
        val fromSlot = slot<Long>()
        every {
            historyRepository.observeInboundDaily("p1", 1, capture(fromSlot), any())
        } returns flowOf(emptyList())

        val vm = createVm()
        val msPerDay = 86_400_000L
        val now = System.currentTimeMillis()
        val todayMidnight = (now / msPerDay) * msPerDay
        val expectedFrom = todayMidnight - 7 * msPerDay

        val collected = mutableListOf<List<DailyPoint>>()
        val job = launch { vm.chartFlow("p1", 1).toList(collected) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(fromSlot.isCaptured)
        // Allow 1 second tolerance for test execution
        assertTrue(
            kotlin.math.abs(fromSlot.captured - expectedFrom) < 1_000L,
            "fromEpoch=${fromSlot.captured} expected~$expectedFrom (diff=${fromSlot.captured - expectedFrom})",
        )
    }

    @Test
    fun `chartFlow D30 uses fromEpoch = today - 30 days`() = runTest {
        val fromSlot = slot<Long>()
        every {
            historyRepository.observeInboundDaily("p1", 2, capture(fromSlot), any())
        } returns flowOf(emptyList())

        val vm = createVm()
        vm.setRange(ChartRange.D30)

        val msPerDay = 86_400_000L
        val now = System.currentTimeMillis()
        val todayMidnight = (now / msPerDay) * msPerDay
        val expectedFrom = todayMidnight - 30 * msPerDay

        val collected = mutableListOf<List<DailyPoint>>()
        val job = launch { vm.chartFlow("p1", 2).toList(collected) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(fromSlot.isCaptured)
        assertTrue(
            kotlin.math.abs(fromSlot.captured - expectedFrom) < 1_000L,
            "fromEpoch=${fromSlot.captured} expected~$expectedFrom",
        )
    }

    // ────────── helpers ──────────

    private fun createVm() = StatsViewModel(panelRepository, xuiClient, historyRepository)

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
}
