package com.firesin.xuipanel.feature.panels.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.repository.PanelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject

data class PanelFormState(
    val name: String = "",
    val baseUrl: String = "",
    val login: String = "",
    val password: String = "",
    val tlsMode: TlsMode = TlsMode.SYSTEM,
)

data class PanelFormErrors(
    val name: String? = null,
    val baseUrl: String? = null,
    val login: String? = null,
    val password: String? = null,
)

/**
 * State passed to the pin-mismatch re-pin dialog.
 *
 * @param existingSpkiFingerprint First 16 hex chars of the stored SPKI (decoded bytes).
 * @param observedSpkiFingerprint First 16 hex chars of the observed SPKI.
 * @param pinnedAtFormatted Human-readable date the existing pin was stored.
 */
data class PinMismatchDialogState(
    val panelName: String,
    val baseUrl: String,
    val existingSpkiFingerprint: String,
    val observedSpkiFingerprint: String,
    val pinnedAtFormatted: String,
    val verifiedByUser: Boolean = false,
)

sealed class PanelAddEditUiState {
    data class Editing(
        val form: PanelFormState,
        val errors: PanelFormErrors = PanelFormErrors(),
        val submitError: DomainError? = null,
        val isEditMode: Boolean = false,
        /** Non-null when the user must confirm re-pinning after a mismatch. */
        val pinMismatchDialog: PinMismatchDialogState? = null,
    ) : PanelAddEditUiState()


    data class Saving(val form: PanelFormState) : PanelAddEditUiState()

    data object Saved : PanelAddEditUiState()
}

@HiltViewModel
class PanelAddEditViewModel @Inject constructor(
    private val repository: PanelRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val panelId: String? = savedStateHandle[ARG_PANEL_ID]

    private val _uiState = MutableStateFlow<PanelAddEditUiState>(
        PanelAddEditUiState.Editing(form = PanelFormState(), isEditMode = panelId != null)
    )
    val uiState: StateFlow<PanelAddEditUiState> = _uiState

    /** Cached existing panel data used to populate the mismatch dialog. */
    private var existingPanel: Panel? = null

    init {
        panelId?.let { id ->
            viewModelScope.launch {
                val panel = repository.get(id) ?: return@launch
                existingPanel = panel
                _uiState.value = PanelAddEditUiState.Editing(
                    form = PanelFormState(
                        name = panel.name,
                        baseUrl = panel.baseUrl,
                        login = panel.login,
                        password = panel.password,
                        tlsMode = panel.tlsMode,
                    ),
                    isEditMode = true,
                )
            }
        }
    }

    fun updateName(value: String) = updateForm { copy(name = value) }
    fun updateBaseUrl(value: String) = updateForm { copy(baseUrl = value) }
    fun updateLogin(value: String) = updateForm { copy(login = value) }
    fun updatePassword(value: String) = updateForm { copy(password = value) }
    fun updateTlsMode(value: TlsMode) = updateForm { copy(tlsMode = value) }

    fun submit() {
        val editing = _uiState.value as? PanelAddEditUiState.Editing ?: return
        val errors = validate(editing.form)
        if (!errors.isValid()) {
            _uiState.value = editing.copy(errors = errors)
            return
        }

        val form = editing.form
        _uiState.value = PanelAddEditUiState.Saving(form)

        viewModelScope.launch {
            val draft = PanelDraft(
                name = form.name.trim(),
                baseUrl = form.baseUrl.trim(),
                login = form.login.trim(),
                password = form.password,
                tlsMode = form.tlsMode,
            )
            val result = if (panelId == null) {
                repository.add(draft)
            } else {
                repository.update(panelId, draft)
            }

            when (result) {
                is Result.Success -> _uiState.value = PanelAddEditUiState.Saved
                is Result.Failure -> {
                    val mismatch = result.error as? DomainError.PinMismatch
                    _uiState.value = PanelAddEditUiState.Editing(
                        form = form,
                        isEditMode = panelId != null,
                        submitError = if (mismatch != null) null else result.error,
                        pinMismatchDialog = mismatch?.let { buildMismatchDialogState(form, it) },
                    )
                }
            }
        }
    }

    fun updateRePinVerifiedCheckbox(checked: Boolean) {
        val editing = _uiState.value as? PanelAddEditUiState.Editing ?: return
        val dialog = editing.pinMismatchDialog ?: return
        _uiState.value = editing.copy(pinMismatchDialog = dialog.copy(verifiedByUser = checked))
    }

    /**
     * Called when the user confirms re-pinning from the mismatch dialog.
     * Uses [PanelRepository.rePin] which probes with a null pin, capturing the new SPKI.
     */
    fun confirmRePin() {
        val editing = _uiState.value as? PanelAddEditUiState.Editing ?: return
        if (editing.pinMismatchDialog?.verifiedByUser != true) return
        val id = panelId ?: return // re-pin only valid in edit mode

        _uiState.value = editing.copy(pinMismatchDialog = null)
        val form = editing.form
        _uiState.value = PanelAddEditUiState.Saving(form)

        viewModelScope.launch {
            val draft = PanelDraft(
                name = form.name.trim(),
                baseUrl = form.baseUrl.trim(),
                login = form.login.trim(),
                password = form.password,
                tlsMode = TlsMode.PINNED,
            )
            when (val result = repository.rePin(id, draft)) {
                is Result.Success -> _uiState.value = PanelAddEditUiState.Saved
                is Result.Failure -> {
                    _uiState.value = PanelAddEditUiState.Editing(
                        form = form,
                        isEditMode = true,
                        submitError = result.error,
                    )
                }
            }
        }
    }

    fun dismissPinMismatchDialog() {
        val editing = _uiState.value as? PanelAddEditUiState.Editing ?: return
        _uiState.value = editing.copy(pinMismatchDialog = null)
    }

    private fun buildMismatchDialogState(
        form: PanelFormState,
        mismatch: DomainError.PinMismatch,
    ): PinMismatchDialogState {
        val stored = existingPanel
        return PinMismatchDialogState(
            panelName = form.name,
            baseUrl = form.baseUrl,
            existingSpkiFingerprint = stored?.pinnedSpkiSha256?.toSpkiFingerprint() ?: "",
            observedSpkiFingerprint = mismatch.observedSpki.toSpkiFingerprint(),
            pinnedAtFormatted = stored?.pinnedAt?.let {
                DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN)
                    .withZone(ZoneId.systemDefault())
                    .format(it)
            } ?: "",
        )
    }

    private fun String.toSpkiFingerprint(): String = runCatching {
        val bytes = Base64.getDecoder().decode(this)
        bytes.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }.getOrDefault(take(FINGERPRINT_CHARS))

    private fun updateForm(block: PanelFormState.() -> PanelFormState) {
        _uiState.update { state ->
            when (state) {
                is PanelAddEditUiState.Editing -> state.copy(
                    form = state.form.block(),
                    submitError = null,
                )
                else -> state
            }
        }
    }

    private fun validate(form: PanelFormState): PanelFormErrors {
        val baseUrlValid = form.baseUrl.trim().let { url ->
            url.startsWith("https://") && isValidUrl(url)
        }
        return PanelFormErrors(
            name = if (form.name.isBlank()) "" else null,
            baseUrl = if (!baseUrlValid) "" else null,
            login = if (form.login.isBlank()) "" else null,
            password = if (form.password.isBlank()) "" else null,
        )
    }

    private fun isValidUrl(url: String): Boolean = runCatching {
        val parsed = URL(url)
        parsed.toURI()
        parsed.userInfo == null
    }.getOrDefault(false)

    private fun PanelFormErrors.isValid() =
        name == null && baseUrl == null && login == null && password == null

    companion object {
        const val ARG_PANEL_ID = "panelId"
        private const val DATE_FORMAT_PATTERN = "dd.MM.yyyy HH:mm"
        private const val FINGERPRINT_BYTES = 8
        private const val FINGERPRINT_CHARS = 16
    }
}
