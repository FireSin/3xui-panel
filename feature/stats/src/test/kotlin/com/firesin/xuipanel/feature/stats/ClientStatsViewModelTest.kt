package com.firesin.xuipanel.feature.stats

import androidx.lifecycle.SavedStateHandle
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ClientStatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var historyRepository: TrafficHistoryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        historyRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ────────── initial state ──────────

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

    // ────────── chartFlow uses correct fromEpoch ──────────

    @Test
    fun `chartFlow D7 calls observeClientDaily with fromEpoch = today minus 7 days`() = runTest {
        val fromSlot = slot<Long>()
        every {
            historyRepository.observeClientDaily(
                panelId = PANEL_ID,
                inboundId = INBOUND_ID,
                emailKey = EMAIL_KEY,
                fromDay = capture(fromSlot),
                toDay = any(),
            )
        } returns flowOf(emptyList())

        val vm = createVm()

        val msPerDay = 86_400_000L
        val now = System.currentTimeMillis()
        val todayMidnight = (now / msPerDay) * msPerDay
        val expectedFrom = todayMidnight - 7 * msPerDay

        val job = launch { vm.chartFlow.toList(mutableListOf()) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(fromSlot.isCaptured)
        assertTrue(
            kotlin.math.abs(fromSlot.captured - expectedFrom) < 1_000L,
            "fromEpoch=${fromSlot.captured}, expected~$expectedFrom",
        )
    }

    @Test
    fun `chartFlow D30 calls observeClientDaily with fromEpoch = today minus 30 days`() = runTest {
        val fromSlot = slot<Long>()
        every {
            historyRepository.observeClientDaily(
                panelId = PANEL_ID,
                inboundId = INBOUND_ID,
                emailKey = EMAIL_KEY,
                fromDay = capture(fromSlot),
                toDay = any(),
            )
        } returns flowOf(emptyList())

        val vm = createVm()
        vm.setRange(ChartRange.D30)

        val msPerDay = 86_400_000L
        val now = System.currentTimeMillis()
        val todayMidnight = (now / msPerDay) * msPerDay
        val expectedFrom = todayMidnight - 30 * msPerDay

        val job = launch { vm.chartFlow.toList(mutableListOf()) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(fromSlot.isCaptured)
        assertTrue(
            kotlin.math.abs(fromSlot.captured - expectedFrom) < 1_000L,
            "fromEpoch=${fromSlot.captured}, expected~$expectedFrom",
        )
    }

    // ────────── SavedStateHandle pass-through ──────────

    @Test
    fun `panelId inboundId emailKey clientLabel are read from SavedStateHandle`() {
        val vm = createVm()
        assertEquals(PANEL_ID, vm.panelId)
        assertEquals(INBOUND_ID, vm.inboundId)
        assertEquals(EMAIL_KEY, vm.emailKey)
        assertEquals(CLIENT_LABEL, vm.clientLabel)
    }

    // ────────── chartFlow returns points from repository ──────────

    @Test
    fun `chartFlow emits points returned by repository`() = runTest {
        val msPerDay = 86_400_000L
        val today = (System.currentTimeMillis() / msPerDay) * msPerDay
        val expected = listOf(
            DailyPoint(today - msPerDay, 1_000L, 2_000L),
            DailyPoint(today, 3_000L, 4_000L),
        )
        every {
            historyRepository.observeClientDaily(any(), any(), any(), any(), any())
        } returns flowOf(expected)

        val vm = createVm()

        val collected = mutableListOf<List<DailyPoint>>()
        val job = launch { vm.chartFlow.toList(collected) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertEquals(expected, collected.last())
    }

    // ────────── helpers ──────────

    private fun createVm() = ClientStatsViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                ClientStatsViewModel.ARG_PANEL_ID to PANEL_ID,
                ClientStatsViewModel.ARG_INBOUND_ID to INBOUND_ID,
                ClientStatsViewModel.ARG_EMAIL_KEY to EMAIL_KEY,
                ClientStatsViewModel.ARG_CLIENT_LABEL to CLIENT_LABEL,
            ),
        ),
        historyRepository = historyRepository,
    )

    private companion object {
        const val PANEL_ID = "panel-42"
        const val INBOUND_ID = 7
        const val EMAIL_KEY = "alice@example.com"
        const val CLIENT_LABEL = "alice@example.com"
    }
}
