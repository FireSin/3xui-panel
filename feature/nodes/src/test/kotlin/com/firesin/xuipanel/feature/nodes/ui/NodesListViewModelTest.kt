package com.firesin.xuipanel.feature.nodes.ui

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.NodeDto
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
class NodesListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
        every { repository.observeAll() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load_success_shows_content`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNodes(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeNode()))

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodesUiState.Content::class.java, state)
        val content = state as NodesUiState.Content
        assertEquals(1, content.nodes.size)
        assertEquals("test-node", content.nodes.first().name)
    }

    @Test
    fun `load_failure_shows_error`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNodes(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodesUiState.Error::class.java, state)
        assertEquals(DomainError.InvalidCredentials, (state as NodesUiState.Error).error)
    }

    @Test
    fun `setEnable_toggles_node`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNodes(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeNode(enable = true)))
        coEvery {
            xuiClient.setNodeEnabled(any(), any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setEnable(id = 1, enable = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.setNodeEnabled(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = 1,
                enable = false,
            )
        }
        // fetchNodes called at least twice: init + after toggle
        coVerify(atLeast = 2) {
            xuiClient.fetchNodes(panel.id, any(), any(), any())
        }
    }

    @Test
    fun `delete_success_refreshes_list`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNodes(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeNode()))
        coEvery {
            xuiClient.deleteNode(any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.delete(id = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.deleteNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = 1,
            )
        }
        coVerify(atLeast = 2) {
            xuiClient.fetchNodes(panel.id, any(), any(), any())
        }
    }

    @Test
    fun `no_active_panel_produces_NoActivePanel_state`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(NodesUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    @Test
    fun `setEnable_failure_surfaces_errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNodes(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeNode()))
        coEvery {
            xuiClient.setNodeEnabled(any(), any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = NodesListViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1) // initial null
            vm.setEnable(id = 1, enable = false)
            testDispatcher.scheduler.advanceUntilIdle()
            val error = awaitItem()
            assertInstanceOf(DomainError.Network::class.java, error)
            cancelAndIgnoreRemainingEvents()
        }

        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

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

    private fun fakeNode(enable: Boolean = true) = NodeDto(
        id = 1,
        name = "test-node",
        scheme = "https",
        address = "node.example.com",
        port = 2053,
        enable = enable,
        status = "online",
    )
}
