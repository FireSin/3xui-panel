package com.firesin.xuipanel.feature.lock

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockUiState {
    data object Idle : LockUiState
    data object Authenticating : LockUiState
    data object Authenticated : LockUiState
    data class Failed(val message: String) : LockUiState
    data object SystemBiometryRemoved : LockUiState
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appSecurityRepository: AppSecurityRepository,
) : ViewModel() {

    val isLockEnabled: StateFlow<Boolean?> = appSecurityRepository.isLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLockOnPauseEnabled: StateFlow<Boolean?> = appSecurityRepository.isLockOnPauseEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow<LockUiState>(LockUiState.Idle)
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    fun onAuthenticating() {
        _uiState.value = LockUiState.Authenticating
    }

    fun onAuthSuccess() {
        _uiState.value = LockUiState.Authenticated
    }

    fun onAuthError(errorCode: Int, message: String) {
        when (errorCode) {
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            -> _uiState.value = LockUiState.SystemBiometryRemoved

            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            -> _uiState.value = LockUiState.Idle

            else -> _uiState.value = LockUiState.Failed(message)
        }
    }

    fun onAuthFailed() {
        // Individual attempt failed (bad fingerprint etc.) — BiometricPrompt handles retries
    }

    /** Resets authenticated state — called on app pause when lock-on-pause is active. */
    fun lock() {
        _uiState.value = LockUiState.Idle
    }

    fun disableLockAndAuthenticate() {
        viewModelScope.launch {
            appSecurityRepository.setLockEnabled(false)
            _uiState.value = LockUiState.Authenticated
        }
    }
}
