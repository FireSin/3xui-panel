package com.firesin.xuipanel.feature.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val panelRepository: PanelRepository,
    private val xuiClient: XuiClient,
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

    // ---- System actions ----

    /**
     * true while a long-running action (updatePanel / installXray) is in flight.
     * Used to show a progress indicator in the UI.
     */
    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading

    /** One-shot snackbar events emitted after each system action completes. */
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun resetAllTraffics(successMsg: String, errorPrefix: String) {
        runSystemAction(showSpinner = false, successMsg = successMsg, errorPrefix = errorPrefix) { panel ->
            xuiClient.resetAllTraffics(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
        }
    }

    fun updatePanel(successMsg: String, errorPrefix: String) {
        runSystemAction(showSpinner = true, successMsg = successMsg, errorPrefix = errorPrefix) { panel ->
            xuiClient.updatePanel(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
        }
    }

    fun installXray(version: String, successMsg: String, errorPrefix: String) {
        runSystemAction(showSpinner = true, successMsg = successMsg, errorPrefix = errorPrefix) { panel ->
            xuiClient.installXray(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                version = version,
            )
        }
    }

    fun backupToTgBot(successMsg: String, errorPrefix: String) {
        runSystemAction(showSpinner = false, successMsg = successMsg, errorPrefix = errorPrefix) { panel ->
            xuiClient.backupToTgBot(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
        }
    }

    /**
     * Resolves the active panel, runs [block], then emits a snackbar message.
     * When no panel is active, emits an error immediately without calling [block].
     */
    private fun runSystemAction(
        showSpinner: Boolean,
        successMsg: String,
        errorPrefix: String,
        block: suspend (Panel) -> Result<Unit, *>,
    ) {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first()
            if (panel == null) {
                _snackbarMessage.tryEmit(context.getString(R.string.settings_system_no_active_panel))
                return@launch
            }
            if (showSpinner) _isActionLoading.value = true
            try {
                when (val result = block(panel)) {
                    is Result.Success<*> -> _snackbarMessage.tryEmit(successMsg)
                    is Result.Failure<*> -> _snackbarMessage.tryEmit("$errorPrefix: ${result.error}")
                }
            } finally {
                if (showSpinner) _isActionLoading.value = false
            }
        }
    }
}
