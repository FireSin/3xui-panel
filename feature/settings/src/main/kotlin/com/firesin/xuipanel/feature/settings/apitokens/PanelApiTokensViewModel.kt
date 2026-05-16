package com.firesin.xuipanel.feature.settings.apitokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PanelApiTokensViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PanelApiTokensUiState>(PanelApiTokensUiState.NoActivePanel)
    val uiState: StateFlow<PanelApiTokensUiState> = _uiState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = PanelApiTokensUiState.NoActivePanel
                } else {
                    _uiState.value = PanelApiTokensUiState.Loading(panel)
                    loadTokens(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            loadTokens(panel)
            _isBusy.value = false
        }
    }

    fun create(name: String, onDone: () -> Unit = {}) {
        val panel = activePanel() ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.createApiToken(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                name = name.trim(),
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> {
                    // Auto-bind the new token to the panel iff none was set yet — subsequent
                    // calls switch to Bearer auth and stop spamming cookie-based re-logins.
                    val created = result.data.token
                    if (panel.apiToken.isNullOrBlank() && created.isNotBlank()) {
                        repository.setApiToken(panel.id, created)
                    }
                    onDone()
                    loadTokens(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun delete(id: Int, deletedMsg: String) {
        val panel = activePanel() ?: return
        val deletedToken = currentTokens().firstOrNull { it.id == id }?.token
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.deleteApiToken(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                id = id,
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> {
                    // If we were using this token for Bearer auth, drop the binding so the
                    // next call falls back to login/password instead of returning 401.
                    if (!deletedToken.isNullOrBlank() && deletedToken == panel.apiToken) {
                        repository.setApiToken(panel.id, null)
                    }
                    _snackbarMessage.value = deletedMsg
                    loadTokens(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        val panel = activePanel() ?: return
        val affectedToken = currentTokens().firstOrNull { it.id == id }?.token
        viewModelScope.launch {
            val result = xuiClient.setApiTokenEnabled(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                id = id,
                enabled = enabled,
            )
            when (result) {
                is Result.Success -> {
                    // Drop the binding if the bound token was disabled — it won't authenticate anymore.
                    if (!enabled && !affectedToken.isNullOrBlank() && affectedToken == panel.apiToken) {
                        repository.setApiToken(panel.id, null)
                    }
                    loadTokens(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    private fun currentTokens(): List<com.firesin.xuipanel.core.xui.dto.ApiTokenDto> =
        (_uiState.value as? PanelApiTokensUiState.Content)?.tokens ?: emptyList()

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private suspend fun loadTokens(panel: Panel) {
        val result = xuiClient.listApiTokens(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> {
                // Auto-bind: if the panel has no Bearer token yet but the panel-side already
                // has an enabled one, adopt it. Lets the app switch to Bearer auth without the
                // user having to copy/paste anything.
                if (panel.apiToken.isNullOrBlank()) {
                    val firstEnabled = result.data.firstOrNull { it.enabled && it.token.isNotBlank() }
                    if (firstEnabled != null) {
                        repository.setApiToken(panel.id, firstEnabled.token)
                    }
                }
                PanelApiTokensUiState.Content(panel, result.data)
            }
            is Result.Failure -> PanelApiTokensUiState.Error(panel, result.error)
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is PanelApiTokensUiState.Content -> s.panel
        is PanelApiTokensUiState.Loading -> s.panel
        is PanelApiTokensUiState.Error -> s.panel
        PanelApiTokensUiState.NoActivePanel -> null
    }
}
