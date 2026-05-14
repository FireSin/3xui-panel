package com.firesin.xuipanel.feature.nodes.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
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
class NodeDetailViewModelTest {

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
    fun `success load all metrics populates Content state`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNode())
        coEvery { xuiClient.fetchNodeHistory(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(fakePoints())

        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeDetailUiState.Content::class.java, state)
        val content = state as NodeDetailUiState.Content
        assertFalse(content.isHistoryLoading)
        assertEquals(NodeHistoryMetric.entries.size, content.histories.size)
        assertTrue(content.histories.values.all { it.size == 2 })
    }

    @Test
    fun `partial failure — one metric fails — others succeed — Content state with partial histories`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNode())

        // Default: all succeed
        coEvery {
            xuiClient.fetchNodeHistory(any(), any(), any(), any(), any(), any(), any())
        } returns Result.Success(fakePoints())
        // CPU metric fails specifically
        coEvery {
            xuiClient.fetchNodeHistory(any(), any(), any(), any(), any(), eq("cpu"), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeDetailUiState.Content::class.java, state)
        val content = state as NodeDetailUiState.Content
        // CPU history is empty (failure treated as empty)
        assertEquals(emptyList<ServerHistoryPointDto>(), content.histories[NodeHistoryMetric.CPU])
        // Others have data
        assertTrue(content.histories[NodeHistoryMetric.MEM]!!.isNotEmpty())
    }

    @Test
    fun `empty list from server renders as Content with empty histories`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNode())
        coEvery { xuiClient.fetchNodeHistory(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(emptyList())

        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeDetailUiState.Content::class.java, state)
        val content = state as NodeDetailUiState.Content
        assertTrue(content.histories.values.all { it.isEmpty() })
    }

    @Test
    fun `fetchNode failure transitions to Error state`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(NodeDetailUiState.Error::class.java, vm.uiState.value)
    }

    @Test
    fun `selectMetric updates selectedMetric in Content`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNode())
        coEvery { xuiClient.fetchNodeHistory(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(fakePoints())

        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            skipItems(1)
            vm.selectMetric(NodeHistoryMetric.LATENCY)
            val updated = awaitItem()
            assertInstanceOf(NodeDetailUiState.Content::class.java, updated)
            assertEquals(NodeHistoryMetric.LATENCY, (updated as NodeDetailUiState.Content).selectedMetric)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- helpers ----

    private fun buildVm() = NodeDetailViewModel(
        repository = repository,
        xuiClient = xuiClient,
        savedStateHandle = SavedStateHandle(mapOf("nodeId" to NODE_ID)),
    )

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

    private fun fakeNode() = NodeDto(
        id = NODE_ID,
        name = "test-node",
        scheme = "https",
        address = "node.example.com",
        port = 2053,
        status = "online",
    )

    private fun fakePoints() = listOf(
        ServerHistoryPointDto(t = 0L, v = 10.0),
        ServerHistoryPointDto(t = 120L, v = 20.0),
    )

    private companion object {
        const val NODE_ID = 42
    }
}
