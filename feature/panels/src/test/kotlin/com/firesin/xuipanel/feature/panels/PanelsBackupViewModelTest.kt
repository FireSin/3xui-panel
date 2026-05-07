package com.firesin.xuipanel.feature.panels

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.crypto.BackupError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.BackupRepository
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.feature.panels.ui.PanelsListEvent
import com.firesin.xuipanel.feature.panels.ui.PanelsListUiState
import com.firesin.xuipanel.feature.panels.ui.PanelsListViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PanelsBackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val backupRepository: BackupRepository = mockk()
    private val panelRepository: PanelRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { panelRepository.observeAll() } returns flowOf(emptyList())
        every { panelRepository.observeActive() } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makePanel(id: String = "p1") = Panel(
        id = id, name = "Test", baseUrl = "https://example.com",
        login = "admin", password = "pass",
        tlsMode = TlsMode.SYSTEM, pinnedSpkiSha256 = null, pinnedAt = null,
        isActive = true,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastLoginAt = null,
    )

    private fun buildVm() = PanelsListViewModel(panelRepository, backupRepository)

    @Test
    fun `export success emits OpenSafCreate`() = runTest {
        every { panelRepository.observeAll() } returns flowOf(listOf(makePanel()))
        every { panelRepository.observeActive() } returns flowOf(makePanel())
        coEvery { backupRepository.exportPanels(any()) } returns Result.Success("{envelope}")

        val vm = buildVm()
        advanceUntilIdle()

        vm.events.test {
            vm.onExportClick()
            advanceUntilIdle()

            val content = vm.uiState.value as? PanelsListUiState.Content
            assertNotNull(content?.exportDialog)

            vm.onExportPasswordChange("securepass")
            vm.onExportConfirmChange("securepass")
            vm.onExportConfirm()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PanelsListEvent.OpenSafCreate, "Expected OpenSafCreate, got $event")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `export failure shows snackbar`() = runTest {
        every { panelRepository.observeAll() } returns flowOf(listOf(makePanel()))
        every { panelRepository.observeActive() } returns flowOf(makePanel())
        coEvery { backupRepository.exportPanels(any()) } returns
            Result.Failure(BackupError.Unexpected(RuntimeException("oops")))

        val vm = buildVm()
        advanceUntilIdle()

        vm.events.test {
            vm.onExportClick()
            advanceUntilIdle()

            vm.onExportPasswordChange("securepass")
            vm.onExportConfirmChange("securepass")
            vm.onExportConfirm()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PanelsListEvent.ShowSnackbar, "Expected ShowSnackbar, got $event")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import wrong passphrase shows snackbar`() = runTest {
        coEvery { backupRepository.importPanels(any(), any()) } returns
            Result.Failure(BackupError.WrongPassphrase)

        val vm = buildVm()
        advanceUntilIdle()

        vm.onFileContentRead("{valid-looking-json}")
        advanceUntilIdle()

        vm.events.test {
            vm.onImportPasswordChange("somepass")
            vm.onImportConfirm()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PanelsListEvent.ShowSnackbar, "Expected ShowSnackbar, got $event")
            val msg = (event as PanelsListEvent.ShowSnackbar).message
            assertTrue(msg.contains("парольная", ignoreCase = true), "Expected passphrase message, got: $msg")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import with existing panels triggers confirm dialog`() = runTest {
        every { panelRepository.observeAll() } returns flowOf(listOf(makePanel()))
        every { panelRepository.observeActive() } returns flowOf(makePanel())

        val vm = buildVm()
        advanceUntilIdle()

        vm.onFileContentRead("{json}")
        advanceUntilIdle()

        vm.onImportPasswordChange("anypass1")
        vm.onImportConfirm()
        advanceUntilIdle()

        val content = vm.uiState.value as? PanelsListUiState.Content
        assertNotNull(content?.confirmImportDialog)
        assertEquals(1, content?.confirmImportDialog?.existingCount)
        assertNull(content?.importDialog)
    }

    @Test
    fun `export dialog validation — short passphrase is invalid`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.onExportClick()
        advanceUntilIdle()

        vm.onExportPasswordChange("short")
        vm.onExportConfirmChange("short")

        val content = vm.uiState.value as? PanelsListUiState.Content
        val dialog = content?.exportDialog
        assertNotNull(dialog)
        val isValid = dialog!!.password.length >= PanelsListViewModel.MIN_PASSPHRASE_LENGTH &&
            dialog.password == dialog.confirm
        assertEquals(false, isValid)
    }
}
