package com.firesin.xuipanel.feature.panels.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.repository.PanelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

data class PanelFormState(
    val name: String = "",
    val baseUrl: String = "",
    val login: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
)

data class PanelFormErrors(
    val name: String? = null,
    val baseUrl: String? = null,
    val login: String? = null,
    val password: String? = null,
)

sealed class PanelAddEditUiState {
    data class Editing(
        val form: PanelFormState,
        val errors: PanelFormErrors = PanelFormErrors(),
        val submitError: DomainError? = null,
        val isEditMode: Boolean = false,
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

    init {
        panelId?.let { id ->
            viewModelScope.launch {
                val panel = repository.get(id) ?: return@launch
                _uiState.value = PanelAddEditUiState.Editing(
                    form = PanelFormState(
                        name = panel.name,
                        baseUrl = panel.baseUrl,
                        login = panel.login,
                        password = panel.password,
                        trustSelfSigned = panel.trustSelfSigned,
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
    fun updateTrustSelfSigned(value: Boolean) = updateForm { copy(trustSelfSigned = value) }

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
                trustSelfSigned = form.trustSelfSigned,
            )
            val result = if (panelId == null) {
                repository.add(draft)
            } else {
                repository.update(panelId, draft)
            }

            when (result) {
                is Result.Success -> _uiState.value = PanelAddEditUiState.Saved
                is Result.Failure -> _uiState.value = PanelAddEditUiState.Editing(
                    form = form,
                    isEditMode = panelId != null,
                    submitError = result.error,
                )
            }
        }
    }

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
            url.startsWith("https://") && !url.endsWith("/") && isValidUrl(url)
        }
        return PanelFormErrors(
            name = if (form.name.isBlank()) "" else null,
            baseUrl = if (!baseUrlValid) "" else null,
            login = if (form.login.isBlank()) "" else null,
            password = if (form.password.isBlank()) "" else null,
        )
    }

    private fun isValidUrl(url: String): Boolean = runCatching { URL(url).toURI() }.isSuccess

    private fun PanelFormErrors.isValid() =
        name == null && baseUrl == null && login == null && password == null

    companion object {
        const val ARG_PANEL_ID = "panelId"
    }
}
