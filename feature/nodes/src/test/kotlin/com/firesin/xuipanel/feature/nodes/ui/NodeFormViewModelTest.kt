package com.firesin.xuipanel.feature.nodes.ui

import androidx.lifecycle.SavedStateHandle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.AddNodeRequestDto
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.dto.NodeStatusProbeDto
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NodeFormViewModelTest {

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
    fun `load_for_edit_seeds_form`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNodeDto())

        val vm = NodeFormViewModel(repository, xuiClient, SavedStateHandle(mapOf("nodeId" to 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeFormUiState.Editing::class.java, state)
        val editing = state as NodeFormUiState.Editing
        assertEquals("test-node", editing.fields.name)
        assertEquals("https", editing.fields.scheme)
        assertEquals("node.example.com", editing.fields.address)
        assertEquals("2053", editing.fields.port)
        assertEquals(1, editing.editingNodeId)
    }

    @Test
    fun `test_connection_success`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)

        val probe = NodeStatusProbeDto(status = "online", latencyMs = 42, xrayVersion = "25.4.0")
        coEvery { xuiClient.testNode(any(), any(), any(), any(), any()) } returns
            Result.Success(probe)

        val vm = NodeFormViewModel(repository, xuiClient, SavedStateHandle())
        // Pre-fill required fields
        vm.updateName("my-node")
        vm.updateAddress("1.2.3.4")
        vm.updatePort("2053")
        vm.updateApiToken("secret-token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeFormUiState.Editing::class.java, state)
        val editing = state as NodeFormUiState.Editing
        val testResult = editing.testResult
        assertInstanceOf(NodeFormUiState.TestResult.Success::class.java, testResult)
        assertEquals(42, (testResult as NodeFormUiState.TestResult.Success).probe.latencyMs)
    }

    @Test
    fun `test_connection_failure`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)

        coEvery { xuiClient.testNode(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("refused")))

        val vm = NodeFormViewModel(repository, xuiClient, SavedStateHandle())
        vm.updateName("my-node")
        vm.updateAddress("1.2.3.4")
        vm.updatePort("2053")
        vm.updateApiToken("secret-token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.testConnection()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(NodeFormUiState.Editing::class.java, state)
        val editing = state as NodeFormUiState.Editing
        val testResult = editing.testResult
        assertInstanceOf(NodeFormUiState.TestResult.Failure::class.java, testResult)
        assertTrue((testResult as NodeFormUiState.TestResult.Failure).message.isNotBlank())
    }

    @Test
    fun `save_in_create_mode_calls_addNode`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.addNode(any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = NodeFormViewModel(repository, xuiClient, SavedStateHandle())
        vm.updateName("my-node")
        vm.updateAddress("1.2.3.4")
        vm.updatePort("2053")
        vm.updateApiToken("secret-token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.addNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                body = any<AddNodeRequestDto>(),
            )
        }
        assertInstanceOf(NodeFormUiState.Saved::class.java, vm.uiState.value)
    }

    @Test
    fun `save_in_edit_mode_calls_updateNode`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNode(any(), any(), any(), any(), any()) } returns
            Result.Success(fakeNodeDto())
        coEvery { xuiClient.updateNode(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = NodeFormViewModel(repository, xuiClient, SavedStateHandle(mapOf("nodeId" to 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        // Ensure apiToken is filled (fakeNodeDto sets it)
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.updateNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = 1,
                body = any<AddNodeRequestDto>(),
            )
        }
        assertInstanceOf(NodeFormUiState.Saved::class.java, vm.uiState.value)
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

    private fun fakeNodeDto() = NodeDto(
        id = 1,
        name = "test-node",
        scheme = "https",
        address = "node.example.com",
        port = 2053,
        apiToken = "token123",
        enable = true,
        status = "online",
    )
}
