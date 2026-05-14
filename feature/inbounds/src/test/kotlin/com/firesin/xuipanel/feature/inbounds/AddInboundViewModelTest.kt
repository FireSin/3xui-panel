package com.firesin.xuipanel.feature.inbounds

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.X25519KeyPairDto
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundUiState
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundViewModel
import com.firesin.xuipanel.feature.inbounds.add.ui.ProtocolType
import com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AddInboundViewModelTest {

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
    fun `default state has VLESS and Reality and one client`() = runTest {
        every { repository.observeActive() } returns flowOf(null)
        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        val editing = vm.uiState.value as AddInboundUiState.Editing
        assertEquals(ProtocolType.VLESS, editing.formState.selectedProtocol)
        assertEquals(SecurityType.REALITY, editing.formState.selectedSecurity)
        assertEquals(1, editing.formState.vlessClients.size)
        assertTrue(editing.formState.vlessClients[0].id.isNotBlank())
    }

    @Test
    fun `switching protocol preserves common fields`() = runTest {
        every { repository.observeActive() } returns flowOf(null)
        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateRemark("my-remark")
        vm.updatePort("8080")
        vm.updateSelectedProtocol(ProtocolType.VMESS)
        testDispatcher.scheduler.advanceUntilIdle()

        val editing = vm.uiState.value as AddInboundUiState.Editing
        assertEquals("my-remark", editing.formState.remark)
        assertEquals("8080", editing.formState.port)
        assertEquals(ProtocolType.VMESS, editing.formState.selectedProtocol)
    }

    @Test
    fun `save with vless+reality calls xuiClient addInbound and emits Saved`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.addInbound(any(), any(), any(), any(), any()) } returns Result.Success(Unit)

        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateRemark("test-vless")
        vm.updateRealityDest("yahoo.com:443")
        vm.updateRealityServerNames("yahoo.com")
        vm.updateRealityPrivateKey("priv123")
        vm.updateRealityPublicKey("pub456")

        vm.uiState.test {
            skipItems(1) // initial Editing
            vm.save()
            testDispatcher.scheduler.advanceUntilIdle()
            val saved = expectMostRecentItem()
            assertInstanceOf(AddInboundUiState.Saved::class.java, saved)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { xuiClient.addInbound(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `save failure surfaces error message`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.addInbound(any(), any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        val editing = vm.uiState.value as AddInboundUiState.Editing
        assertNotNull(editing.errorMessage)
    }

    @Test
    fun `generateX25519 fills privateKey and publicKey on success`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNewX25519(any(), any(), any(), any()) } returns
            Result.Success(X25519KeyPairDto(privateKey = "priv-abc", publicKey = "pub-xyz"))

        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.generateX25519()
        testDispatcher.scheduler.advanceUntilIdle()

        val editing = vm.uiState.value as AddInboundUiState.Editing
        assertEquals("priv-abc", editing.formState.realityPrivateKey)
        assertEquals("pub-xyz", editing.formState.realityPublicKey)
    }

    @Test
    fun `generateX25519 failure leaves keys unchanged`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchNewX25519(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = AddInboundViewModel(repository, xuiClient, SavedStateHandle())
        testDispatcher.scheduler.advanceUntilIdle()

        val keyBefore = (vm.uiState.value as AddInboundUiState.Editing).formState.realityPrivateKey

        vm.generateX25519()
        testDispatcher.scheduler.advanceUntilIdle()

        val keyAfter = (vm.uiState.value as AddInboundUiState.Editing).formState.realityPrivateKey
        assertEquals(keyBefore, keyAfter)
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
}
