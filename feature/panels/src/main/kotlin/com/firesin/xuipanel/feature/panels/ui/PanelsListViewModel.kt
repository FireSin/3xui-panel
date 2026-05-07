package com.firesin.xuipanel.feature.panels.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.crypto.BackupError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.BackupRepository
import com.firesin.xuipanel.core.data.repository.PanelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ──────────────────────────── UI State ────────────────────────────

sealed class PanelsListUiState {
    data object Loading : PanelsListUiState()
    data class Content(
        val panels: List<Panel>,
        val active: Panel?,
        val exportDialog: ExportDialogState? = null,
        val importDialog: ImportDialogState? = null,
        val confirmImportDialog: ConfirmImportDialogState? = null,
        val isBackupLoading: Boolean = false,
    ) : PanelsListUiState()
}

data class ExportDialogState(
    val password: String = "",
    val confirm: String = "",
)

data class ImportDialogState(
    val password: String = "",
    val envelopeJson: String? = null,
)

data class ConfirmImportDialogState(
    val envelopeJson: String,
    val passphrase: CharArray,
    val existingCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConfirmImportDialogState) return false
        return envelopeJson == other.envelopeJson && existingCount == other.existingCount
    }

    override fun hashCode(): Int = envelopeJson.hashCode() * 31 + existingCount
}

// ──────────────────────────── Events ────────────────────────────

sealed class PanelsListEvent {
    data class OpenSafCreate(val suggestedFileName: String) : PanelsListEvent()
    data object OpenSafOpen : PanelsListEvent()
    data class WriteToUri(val uri: Uri, val content: String) : PanelsListEvent()
    data class ReadFromUri(val uri: Uri) : PanelsListEvent()
    data class ShowSnackbar(val message: String) : PanelsListEvent()
}

// ──────────────────────────── ViewModel ────────────────────────────

@HiltViewModel
class PanelsListViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PanelsListUiState>(PanelsListUiState.Loading)
    val uiState: StateFlow<PanelsListUiState> = _uiState

    private val _errorMessage = MutableStateFlow<DomainError?>(null)
    val errorMessage: StateFlow<DomainError?> = _errorMessage

    private val _events = MutableSharedFlow<PanelsListEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PanelsListEvent> = _events.asSharedFlow()

    init {
        combine(
            repository.observeAll(),
            repository.observeActive(),
        ) { panels, active ->
            val current = _uiState.value
            if (current is PanelsListUiState.Content) {
                current.copy(panels = panels, active = active)
            } else {
                PanelsListUiState.Content(panels = panels, active = active)
            }
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun setActive(id: String) {
        viewModelScope.launch {
            when (val result = repository.setActive(id)) {
                is Result.Failure -> _errorMessage.value = result.error
                is Result.Success -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is Result.Failure -> _errorMessage.value = result.error
                is Result.Success -> Unit
            }
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    // ── Export flow ──

    fun onExportClick() {
        updateContent { it.copy(exportDialog = ExportDialogState()) }
    }

    fun onExportPasswordChange(value: String) {
        updateContent { s -> s.copy(exportDialog = s.exportDialog?.copy(password = value)) }
    }

    fun onExportConfirmChange(value: String) {
        updateContent { s -> s.copy(exportDialog = s.exportDialog?.copy(confirm = value)) }
    }

    fun onExportDialogDismiss() {
        updateContent { it.copy(exportDialog = null) }
    }

    fun onExportConfirm() {
        val dialog = contentState?.exportDialog ?: return
        if (dialog.password.length < MIN_PASSPHRASE_LENGTH || dialog.password != dialog.confirm) return

        viewModelScope.launch {
            updateContent { it.copy(isBackupLoading = true, exportDialog = null) }
            val result = backupRepository.exportPanels(dialog.password.toCharArray())
            updateContent { it.copy(isBackupLoading = false) }
            when (result) {
                is Result.Success -> {
                    val date = LocalDate.now().toString()
                    _events.emit(PanelsListEvent.OpenSafCreate("xui-backup-$date.json"))
                    pendingExportContent = result.data
                }
                is Result.Failure -> {
                    _events.emit(PanelsListEvent.ShowSnackbar(result.error.toUserMessage()))
                }
            }
        }
    }

    fun onSafCreateResult(uri: Uri) {
        val content = pendingExportContent ?: return
        pendingExportContent = null
        viewModelScope.launch {
            val count = (contentState?.panels?.size) ?: 0
            _events.emit(PanelsListEvent.WriteToUri(uri, content))
            _events.emit(PanelsListEvent.ShowSnackbar("Экспортировано $count панелей"))
        }
    }

    private var pendingExportContent: String? = null

    // ── Import flow ──

    fun onImportClick() {
        updateContent { it.copy(importDialog = ImportDialogState()) }
        viewModelScope.launch { _events.emit(PanelsListEvent.OpenSafOpen) }
    }

    fun onSafOpenResult(uri: Uri) {
        viewModelScope.launch {
            _events.emit(PanelsListEvent.ReadFromUri(uri))
        }
    }

    fun onFileContentRead(content: String) {
        updateContent { it.copy(importDialog = ImportDialogState(envelopeJson = content)) }
    }

    fun onFileReadError() {
        updateContent { it.copy(importDialog = null) }
        viewModelScope.launch { _events.emit(PanelsListEvent.ShowSnackbar("Файл повреждён")) }
    }

    fun onImportPasswordChange(value: String) {
        updateContent { s -> s.copy(importDialog = s.importDialog?.copy(password = value)) }
    }

    fun onImportDialogDismiss() {
        updateContent { it.copy(importDialog = null) }
    }

    fun onImportConfirm() {
        val dialog = contentState?.importDialog ?: return
        val envelope = dialog.envelopeJson ?: return
        if (dialog.password.isEmpty()) return

        viewModelScope.launch {
            val existingCount = repository.observeAll().first().size
            if (existingCount > 0) {
                updateContent {
                    it.copy(
                        importDialog = null,
                        confirmImportDialog = ConfirmImportDialogState(
                            envelopeJson = envelope,
                            passphrase = dialog.password.toCharArray(),
                            existingCount = existingCount,
                        ),
                    )
                }
            } else {
                updateContent { it.copy(importDialog = null) }
                doImport(envelope, dialog.password.toCharArray())
            }
        }
    }

    fun onConfirmImportDismiss() {
        contentState?.confirmImportDialog?.passphrase?.fill(' ')
        updateContent { it.copy(confirmImportDialog = null) }
    }

    fun onConfirmImportProceed() {
        val confirm = contentState?.confirmImportDialog ?: return
        updateContent { it.copy(confirmImportDialog = null) }
        viewModelScope.launch {
            doImport(confirm.envelopeJson, confirm.passphrase)
        }
    }

    private suspend fun doImport(envelopeJson: String, passphrase: CharArray) {
        updateContent { it.copy(isBackupLoading = true) }
        val result = backupRepository.importPanels(envelopeJson, passphrase)
        updateContent { it.copy(isBackupLoading = false) }
        when (result) {
            is Result.Success -> {
                _events.emit(PanelsListEvent.ShowSnackbar("Импортировано ${result.data} панелей"))
            }
            is Result.Failure -> {
                val msg = when (result.error) {
                    BackupError.WrongPassphrase -> "Неверная парольная фраза"
                    else -> result.error.toUserMessage()
                }
                _events.emit(PanelsListEvent.ShowSnackbar(msg))
            }
        }
    }

    private val contentState: PanelsListUiState.Content?
        get() = _uiState.value as? PanelsListUiState.Content

    private inline fun updateContent(block: (PanelsListUiState.Content) -> PanelsListUiState.Content) {
        _uiState.update { state ->
            if (state is PanelsListUiState.Content) block(state) else state
        }
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}

private fun BackupError.toUserMessage(): String = when (this) {
    BackupError.WrongPassphrase -> "Неверная парольная фраза"
    BackupError.MalformedBundle -> "Файл повреждён или не является резервной копией"
    is BackupError.Unexpected -> "Ошибка: ${cause::class.simpleName}"
}
