package com.firesin.xuipanel.feature.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockToggleState {
    data class Available(val enabled: Boolean) : LockToggleState
    data class Unavailable(val reason: String) : LockToggleState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSecurityRepository: AppSecurityRepository,
) : ViewModel() {

    private val biometricManager = BiometricManager.from(context)

    private val canAuthenticate: Boolean
        get() {
            val result = biometricManager.canAuthenticate(
                BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
            )
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

    val lockToggleState: StateFlow<LockToggleState> = appSecurityRepository.isLockEnabled
        .map { enabled ->
            if (canAuthenticate) {
                LockToggleState.Available(enabled)
            } else {
                LockToggleState.Unavailable(
                    context.getString(R.string.settings_lock_unavailable_hint),
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            if (canAuthenticate) LockToggleState.Available(false)
            else LockToggleState.Unavailable(
                context.getString(R.string.settings_lock_unavailable_hint),
            ),
        )

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSecurityRepository.setLockEnabled(enabled)
            if (!enabled) {
                appSecurityRepository.setLockOnPauseEnabled(false)
            }
        }
    }

    /**
     * Enabled iff main lock is on AND biometric is available.
     * Value = lockOnPause pref; disabled (false) when main lock is off.
     */
    val lockOnPauseEnabled: StateFlow<Boolean> = combine(
        appSecurityRepository.isLockEnabled,
        appSecurityRepository.isLockOnPauseEnabled,
    ) { lockEnabled, onPause ->
        lockEnabled && onPause
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLockOnPauseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSecurityRepository.setLockOnPauseEnabled(enabled)
        }
    }

    val themeMode: StateFlow<ThemeMode> = appSecurityRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSecurityRepository.setThemeMode(mode)
        }
    }

    val installId: StateFlow<String> = appSecurityRepository.installId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
}
