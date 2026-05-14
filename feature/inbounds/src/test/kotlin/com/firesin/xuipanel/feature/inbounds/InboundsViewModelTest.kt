package com.firesin.xuipanel.feature.inbounds

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.inbounds.ui.InboundsUiState
import com.firesin.xuipanel.feature.inbounds.ui.InboundsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class InboundsViewModelTest {

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

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(InboundsUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    @Test
    fun `active panel triggers fetchInbounds and emits Content`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))

        val vm = InboundsViewModel(repository, xuiClient)

        vm.uiState.test {
            skipItems(1) // initial NoActivePanel
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(InboundsUiState.Content::class.java, state)
            val content = state as InboundsUiState.Content
            assertEquals(panel.id, content.panel.id)
            assertEquals(1, content.inbounds.size)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            xuiClient.fetchInbounds(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
        }
    }

    @Test
    fun `fetch failure emits Error state`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(InboundsUiState.Error::class.java, state)
        assertEquals(DomainError.InvalidCredentials, (state as InboundsUiState.Error).error)
    }

    @Test
    fun `successful toggle calls setInboundEnabled then refetches`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.setInboundEnabled(any(), any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggle(id = 1, enable = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.setInboundEnabled(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                enabled = false,
                id = 1,
            )
        }
        // fetchInbounds called once on init + once after toggle
        coVerify(atLeast = 2) {
            xuiClient.fetchInbounds(panel.id, any(), any(), any())
        }
    }

    @Test
    fun `toggle failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.setInboundEnabled(any(), any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1) // initial null
            vm.toggle(id = 1, enable = false)
            testDispatcher.scheduler.advanceUntilIdle()
            val error = awaitItem()
            assertInstanceOf(DomainError.Network::class.java, error)
            cancelAndIgnoreRemainingEvents()
        }

        // errorShown clears the message
        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `successful delete calls deleteInbound then refetches`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.deleteInbound(any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.delete(id = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.deleteInbound(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = 1,
            )
        }
        coVerify(atLeast = 2) {
            xuiClient.fetchInbounds(panel.id, any(), any(), any())
        }
    }

    @Test
    fun `refresh re-fetches inbounds`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) {
            xuiClient.fetchInbounds(panel.id, any(), any(), any())
        }
    }

    @Test
    fun `toggle with no active panel is no-op`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggle(id = 1, enable = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            xuiClient.setInboundEnabled(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `delete with no active panel is no-op`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.delete(id = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            xuiClient.deleteInbound(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `delete failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.deleteInbound(any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1) // initial null
            vm.delete(id = 1)
            testDispatcher.scheduler.advanceUntilIdle()
            val error = awaitItem()
            assertInstanceOf(DomainError.Network::class.java, error)
            cancelAndIgnoreRemainingEvents()
        }

        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `refresh sets isRefreshing flag`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.isRefreshing.test {
            skipItems(1) // initial false
            vm.refresh()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh with no active panel is no-op`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) {
            xuiClient.fetchInbounds(any(), any(), any(), any())
        }
    }

    @Test
    fun `switching active panel fetches for new panel`() = runTest {
        val panel1 = fakePanel().copy(id = "p1")
        val panel2 = fakePanel().copy(id = "p2")
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))

        val activePanel = MutableStateFlow<Panel?>(panel1)
        every { repository.observeActive() } returns activePanel

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify first panel was fetched
        coVerify(exactly = 1) {
            xuiClient.fetchInbounds(panel1.id, panel1.baseUrl, panel1.toAuth(), panel1.toPanelTls())
        }

        // Switch to second panel
        activePanel.value = panel2
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify second panel was also fetched
        coVerify {
            xuiClient.fetchInbounds(panel2.id, panel2.baseUrl, panel2.toAuth(), panel2.toPanelTls())
        }
    }

    @Test
    fun `errorShown clears errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.setInboundEnabled(any(), any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.InvalidCredentials)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggle(id = 1, enable = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DomainError.InvalidCredentials, vm.errorMessage.value)
        vm.errorShown()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `copyClients success calls xuiClient then refetches`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.copyClients(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.copyClients(targetInboundId = 1, sourceInboundId = 2, clientEmails = emptyList(), flow = null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.copyClients(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                targetInboundId = 1,
                sourceInboundId = 2,
                clientEmails = emptyList(),
                flow = null,
            )
        }
        // fetchInbounds called once on init + once after copyClients
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(panel.id, any(), any(), any()) }
    }

    @Test
    fun `copyClients failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.copyClients(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.copyClients(targetInboundId = 1, sourceInboundId = 2, clientEmails = emptyList(), flow = null)
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(DomainError.Network::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importInbounds success calls xuiClient then refetches`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.importInbounds(any(), any(), any(), any(), any())
        } returns Result.Success(Unit)

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.importInbounds("""{"remark":"test","port":443}""")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            xuiClient.importInbounds(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                jsonText = """{"remark":"test","port":443}""",
            )
        }
        coVerify(atLeast = 2) { xuiClient.fetchInbounds(panel.id, any(), any(), any()) }
    }

    @Test
    fun `importInbounds failure surfaces in errorMessage`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchInbounds(any(), any(), any(), any()) } returns
            Result.Success(listOf(fakeInbound()))
        coEvery {
            xuiClient.importInbounds(any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.PanelResponse(0, "invalid json"))

        val vm = InboundsViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.errorMessage.test {
            skipItems(1)
            vm.importInbounds("bad json")
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(DomainError.PanelResponse::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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

    private fun fakeInbound() = InboundDto(
        id = 1,
        remark = "test",
        port = 443,
        protocol = "vmess",
        enable = true,
        up = 0L,
        down = 0L,
        total = 0L,
        expiryTime = 0L,
        listen = "",
        settings = "{}",
        streamSettings = "{}",
        tag = "inbound-443",
        sniffing = "{}",
    )
}
