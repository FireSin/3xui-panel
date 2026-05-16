package com.firesin.xuipanel.feature.settings

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.AppSecurityRepository
import com.firesin.xuipanel.core.data.repository.AutoBackupPreferences
import com.firesin.xuipanel.core.data.repository.AutoBackupSchedule
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.sampler.BackupScheduler
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
    private val autoBackupPreferences: AutoBackupPreferences,
    private val backupScheduler: BackupScheduler,
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

    /** Active panel — drives the "settings apply to <name>" banner. */
    val activePanel: StateFlow<Panel?> = panelRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    // ---- Bundle A: server info ----

    /** Installed Xray version string, null while loading or on error. */
    private val _xrayVersion = MutableStateFlow<String?>(null)
    val xrayVersion: StateFlow<String?> = _xrayVersion

    /** Panel update info; null while not yet loaded. */
    private val _panelUpdateInfo = MutableStateFlow<PanelUpdateState>(PanelUpdateState.Idle)
    val panelUpdateInfo: StateFlow<PanelUpdateState> = _panelUpdateInfo

    fun loadXrayVersion() {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first() ?: return@launch
            val result = xuiClient.fetchXrayVersion(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            _xrayVersion.value = (result as? Result.Success)?.data
        }
    }

    fun loadPanelUpdateInfo() {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first() ?: return@launch
            _panelUpdateInfo.value = PanelUpdateState.Loading
            val result = xuiClient.fetchPanelUpdateInfo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            _panelUpdateInfo.value = when (result) {
                is Result.Success -> PanelUpdateState.Loaded(result.data)
                is Result.Failure -> PanelUpdateState.Idle
            }
        }
    }

    // ---- Bundle B: backup / config ----

    /** Opaque one-shot events emitted by backup/config operations. */
    sealed interface BackupEvent {
        data object DbDownloaded : BackupEvent
        data object DbRestored : BackupEvent
        data object GeofileUpdated : BackupEvent
        data class ConfigLoaded(val json: String) : BackupEvent
        data class Error(val message: String) : BackupEvent
    }

    private val _backupEvent = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvent: SharedFlow<BackupEvent> = _backupEvent.asSharedFlow()

    /**
     * Downloads the panel DB and writes it to the user-chosen SAF [uri].
     * Streams bytes from the panel into the SAF output stream to avoid OOM.
     */
    fun downloadDb(uri: Uri) {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first() ?: run {
                _snackbarMessage.tryEmit(context.getString(R.string.settings_system_no_active_panel))
                return@launch
            }
            _isActionLoading.value = true
            try {
                val result = xuiClient.fetchDbInto(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                ) { inputStream ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            inputStream.copyTo(out, bufferSize = DB_BUFFER_SIZE)
                        }
                    }
                }
                when (result) {
                    is Result.Success -> _backupEvent.tryEmit(BackupEvent.DbDownloaded)
                    is Result.Failure -> _backupEvent.tryEmit(BackupEvent.Error(result.error.toString()))
                }
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    /**
     * Reads the SQLite file at [uri] and uploads it to restore the panel DB.
     * Panel restarts on success.
     */
    fun importDb(uri: Uri) {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first() ?: run {
                _snackbarMessage.tryEmit(context.getString(R.string.settings_system_no_active_panel))
                return@launch
            }
            _isActionLoading.value = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    _backupEvent.tryEmit(BackupEvent.Error(context.getString(R.string.settings_backup_import_read_error)))
                    return@launch
                }
                val fileName = uri.lastPathSegment ?: DB_DEFAULT_FILENAME
                val result = xuiClient.importDb(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                    fileBytes = bytes,
                    fileName = fileName,
                )
                when (result) {
                    is Result.Success -> _backupEvent.tryEmit(BackupEvent.DbRestored)
                    is Result.Failure -> _backupEvent.tryEmit(BackupEvent.Error(result.error.toString()))
                }
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    /**
     * Triggers a refresh of all built-in GeoIP/GeoSite data files.
     */
    fun updateGeofile(successMsg: String, errorPrefix: String) {
        runSystemAction(showSpinner = true, successMsg = successMsg, errorPrefix = errorPrefix) { panel ->
            xuiClient.updateBuiltinGeofile(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
        }
    }

    /**
     * Fetches the Xray config JSON and emits it via [backupEvent] for display in a dialog.
     */
    fun loadConfigJson() {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first() ?: run {
                _snackbarMessage.tryEmit(context.getString(R.string.settings_system_no_active_panel))
                return@launch
            }
            _isActionLoading.value = true
            try {
                val result = xuiClient.fetchConfigJson(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                )
                when (result) {
                    is Result.Success -> _backupEvent.tryEmit(BackupEvent.ConfigLoaded(result.data))
                    is Result.Failure -> _backupEvent.tryEmit(BackupEvent.Error(result.error.toString()))
                }
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    // ---- Auto backup ----

    /** Currently selected SAF folder URI; null = not chosen. */
    val autoBackupTargetUri: StateFlow<Uri?> = autoBackupPreferences.targetUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Resolved folder display name, or null when URI not set or invalid. */
    val autoBackupFolderName: StateFlow<String?> = autoBackupPreferences.targetUri
        .map { uri -> uri?.let { DocumentFile.fromTreeUri(context, it)?.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val autoBackupSchedule: StateFlow<AutoBackupSchedule> = autoBackupPreferences.schedule
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupSchedule.OFF)

    val autoBackupLastRunAt: StateFlow<Long?> = autoBackupPreferences.lastRunAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val autoBackupLastResult: StateFlow<String?> = autoBackupPreferences.lastResult
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Called after the user picks a SAF folder in [OpenDocumentTree].
     * Persists the URI (after taking persistable permission in the UI),
     * then re-applies the schedule.
     */
    fun onAutoBackupFolderPicked(uri: Uri) {
        viewModelScope.launch {
            autoBackupPreferences.setTargetUri(uri)
            // Re-apply to reschedule if schedule was already set (it was OFF before folder was set)
            val schedule = autoBackupPreferences.schedule.first()
            if (schedule != AutoBackupSchedule.OFF) {
                backupScheduler.apply(WorkManager.getInstance(context), schedule)
            }
        }
    }

    /**
     * Changes the backup schedule.
     * If [schedule] != OFF and no folder is set → shows snackbar and keeps OFF.
     */
    fun setAutoBackupSchedule(schedule: AutoBackupSchedule) {
        viewModelScope.launch {
            if (schedule != AutoBackupSchedule.OFF) {
                val uri = autoBackupPreferences.targetUri.first()
                if (uri == null) {
                    _snackbarMessage.tryEmit(context.getString(R.string.settings_autobackup_no_folder))
                    return@launch
                }
            }
            autoBackupPreferences.setSchedule(schedule)
            backupScheduler.apply(WorkManager.getInstance(context), schedule)
        }
    }

    /** Enqueues an immediate one-shot backup (does not affect periodic schedule). */
    fun triggerAutoBackupNow() {
        backupScheduler.triggerNow(WorkManager.getInstance(context))
    }

    private companion object {
        const val DB_BUFFER_SIZE = 8 * 1024
        const val DB_DEFAULT_FILENAME = "x-ui.db"
    }
}

/** State for the panel update badge. */
sealed interface PanelUpdateState {
    data object Idle : PanelUpdateState
    data object Loading : PanelUpdateState
    data class Loaded(val info: com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoObj) : PanelUpdateState
}
