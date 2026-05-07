package com.firesin.xuipanel.feature.lock

import androidx.biometric.BiometricPrompt
import app.cash.turbine.test
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AppSecurityRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(lockEnabled: Boolean = true): LockViewModel {
        every { repository.isLockEnabled } returns flowOf(lockEnabled)
        return LockViewModel(repository)
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val vm = viewModel()
        assertEquals(LockUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `isLockEnabled reflects repository value`() = runTest {
        val vm = viewModel(lockEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.isLockEnabled.value == true)
    }

    @Test
    fun `isLockEnabled false when disabled`() = runTest {
        val vm = viewModel(lockEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.isLockEnabled.value == true)
    }

    @Test
    fun `onAuthSuccess transitions to Authenticated`() = runTest {
        val vm = viewModel()
        vm.onAuthSuccess()
        assertEquals(LockUiState.Authenticated, vm.uiState.value)
    }

    @Test
    fun `onAuthenticating transitions to Authenticating`() = runTest {
        val vm = viewModel()
        vm.onAuthenticating()
        assertEquals(LockUiState.Authenticating, vm.uiState.value)
    }

    @Test
    fun `onAuthError with ERROR_NO_DEVICE_CREDENTIAL emits SystemBiometryRemoved`() = runTest {
        val vm = viewModel()
        vm.onAuthError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, "no credential")
        assertEquals(LockUiState.SystemBiometryRemoved, vm.uiState.value)
    }

    @Test
    fun `onAuthError with ERROR_NO_BIOMETRICS emits SystemBiometryRemoved`() = runTest {
        val vm = viewModel()
        vm.onAuthError(BiometricPrompt.ERROR_NO_BIOMETRICS, "no biometrics")
        assertEquals(LockUiState.SystemBiometryRemoved, vm.uiState.value)
    }

    @Test
    fun `onAuthError with user cancel resets to Idle`() = runTest {
        val vm = viewModel()
        vm.onAuthenticating()
        vm.onAuthError(BiometricPrompt.ERROR_USER_CANCELED, "canceled")
        assertEquals(LockUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `onAuthError with unknown code emits Failed`() = runTest {
        val vm = viewModel()
        vm.onAuthError(999, "hardware error")
        val state = vm.uiState.value
        assertTrue(state is LockUiState.Failed)
        assertEquals("hardware error", (state as LockUiState.Failed).message)
    }

    @Test
    fun `disableLockAndAuthenticate sets lock disabled and transitions to Authenticated`() = runTest {
        val vm = viewModel()

        vm.disableLockAndAuthenticate()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setLockEnabled(false) }
        assertEquals(LockUiState.Authenticated, vm.uiState.value)
    }

    @Test
    fun `uiState emits sequence Authenticating then Authenticated`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(LockUiState.Idle, awaitItem())
            vm.onAuthenticating()
            assertEquals(LockUiState.Authenticating, awaitItem())
            vm.onAuthSuccess()
            assertEquals(LockUiState.Authenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
