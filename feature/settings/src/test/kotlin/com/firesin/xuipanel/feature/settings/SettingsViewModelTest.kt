package com.firesin.xuipanel.feature.settings

import android.content.Context
import androidx.biometric.BiometricManager
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AppSecurityRepository = mockk(relaxed = true)
    private val panelRepository: PanelRepository = mockk(relaxed = true)
    private val xuiClient: XuiClient = mockk(relaxed = true)
    private val context: Context = mockk()
    private val biometricManager: BiometricManager = mockk()

    private val unavailableHint = "Сначала настройте PIN"
    private val noActivePanelMsg = "Нет активной панели"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        every {
            context.getString(R.string.settings_lock_unavailable_hint)
        } returns unavailableHint
        every {
            context.getString(R.string.settings_system_no_active_panel)
        } returns noActivePanelMsg
        every { repository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { repository.installId } returns flowOf("test-install-id")
        // Default: no active panel
        every { panelRepository.observeActive() } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun biometricAvailable() {
        every {
            biometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun biometricUnavailable() {
        every {
            biometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    private fun viewModel(lockEnabled: Boolean = false): SettingsViewModel {
        every { repository.isLockEnabled } returns flowOf(lockEnabled)
        every { repository.isLockOnPauseEnabled } returns flowOf(false)
        return SettingsViewModel(context, repository, panelRepository, xuiClient)
    }

    // ---- Existing lock/theme tests ----

    @Test
    fun `Available enabled=false when biometric ok and lock disabled`() = runTest {
        biometricAvailable()
        val vm = viewModel(lockEnabled = false)

        vm.lockToggleState.test {
            val state = awaitItem()
            assertTrue(state is LockToggleState.Available)
            assertFalse((state as LockToggleState.Available).enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Available enabled=true when biometric ok and lock enabled`() = runTest {
        biometricAvailable()
        val vm = viewModel(lockEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.lockToggleState.test {
            val state = awaitItem()
            assertTrue(state is LockToggleState.Available)
            assertTrue((state as LockToggleState.Available).enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Unavailable when biometric not enrolled`() = runTest {
        biometricUnavailable()
        val vm = viewModel(lockEnabled = false)

        vm.lockToggleState.test {
            val state = awaitItem()
            assertTrue(state is LockToggleState.Unavailable)
            assertEquals(unavailableHint, (state as LockToggleState.Unavailable).reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLockEnabled delegates to repository`() = runTest {
        biometricAvailable()
        val vm = viewModel(lockEnabled = false)

        vm.setLockEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setLockEnabled(true) }
    }

    @Test
    fun `themeMode initial value is SYSTEM`() = runTest {
        biometricAvailable()
        val vm = viewModel()

        vm.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeMode delegates to repository`() = runTest {
        biometricAvailable()
        val vm = viewModel()

        vm.setThemeMode(ThemeMode.DARK)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setThemeMode(ThemeMode.DARK) }
    }

    // ---- System actions tests ----

    @Test
    fun `resetAllTraffics emits no-active-panel message when panel is null`() = runTest {
        biometricAvailable()
        every { panelRepository.observeActive() } returns flowOf(null)
        val vm = viewModel()

        vm.snackbarMessage.test {
            vm.resetAllTraffics(successMsg = "OK", errorPrefix = "ERR")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(noActivePanelMsg, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetAllTraffics emits success message on Result_Success`() = runTest {
        biometricAvailable()
        val panel = fakePanel()
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.resetAllTraffics(any(), any(), any(), any())
        } returns Result.Success(Unit)
        val vm = viewModel()

        vm.snackbarMessage.test {
            vm.resetAllTraffics(successMsg = "Сброшено", errorPrefix = "Ошибка")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Сброшено", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `backupToTgBot emits success message on Result_Success`() = runTest {
        biometricAvailable()
        val panel = fakePanel()
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.backupToTgBot(any(), any(), any(), any())
        } returns Result.Success(Unit)
        val vm = viewModel()

        vm.snackbarMessage.test {
            vm.backupToTgBot(successMsg = "Отправлено", errorPrefix = "Ошибка")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Отправлено", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installXray sets isActionLoading while running and clears it after`() = runTest {
        biometricAvailable()
        val panel = fakePanel()
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.installXray(any(), any(), any(), any(), any())
        } returns Result.Success(Unit)
        val vm = viewModel()

        vm.installXray(version = "v25.5.16", successMsg = "OK", errorPrefix = "ERR")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.isActionLoading.value)
    }

    @Test
    fun `updatePanel emits error prefix on failure`() = runTest {
        biometricAvailable()
        val panel = fakePanel()
        every { panelRepository.observeActive() } returns flowOf(panel)
        coEvery {
            xuiClient.updatePanel(any(), any(), any(), any())
        } returns Result.Failure(com.firesin.xuipanel.core.common.DomainError.Network(java.io.IOException("timeout")))
        val vm = viewModel()

        vm.snackbarMessage.test {
            vm.updatePanel(successMsg = "OK", errorPrefix = "Ошибка")
            testDispatcher.scheduler.advanceUntilIdle()
            val msg = awaitItem()
            assertTrue(msg.startsWith("Ошибка:"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Helpers ----

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
