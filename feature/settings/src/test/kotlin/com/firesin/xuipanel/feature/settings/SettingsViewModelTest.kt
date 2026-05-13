package com.firesin.xuipanel.feature.settings

import android.content.Context
import androidx.biometric.BiometricManager
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AppSecurityRepository = mockk(relaxed = true)
    private val context: Context = mockk()
    private val biometricManager: BiometricManager = mockk()

    private val unavailableHint = "Сначала настройте PIN"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        every {
            context.getString(R.string.settings_lock_unavailable_hint)
        } returns unavailableHint
        every { repository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { repository.installId } returns flowOf("test-install-id")
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
        return SettingsViewModel(context, repository)
    }

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
}
