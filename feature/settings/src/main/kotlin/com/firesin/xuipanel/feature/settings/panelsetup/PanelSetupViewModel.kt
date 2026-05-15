package com.firesin.xuipanel.feature.settings.panelsetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class PanelSetupViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PanelSetupUiState>(PanelSetupUiState.NoActivePanel)
    val uiState: StateFlow<PanelSetupUiState> = _uiState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = PanelSetupUiState.NoActivePanel
                } else {
                    _uiState.value = PanelSetupUiState.Loading(panel)
                    loadSettings(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            loadSettings(panel)
            _isBusy.value = false
        }
    }

    fun updateFlags(transform: (SubFlags) -> SubFlags) {
        val s = _uiState.value as? PanelSetupUiState.Content ?: return
        _uiState.value = s.copy(flags = transform(s.flags))
    }

    fun saveSubscription(savedMsg: String) {
        val s = _uiState.value as? PanelSetupUiState.Content ?: return
        val panel = s.panel
        val merged = patchSubFields(s.raw, s.flags)
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.updateAllSettings(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                settings = merged,
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> {
                    _snackbarMessage.value = savedMsg
                    loadSettings(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun changeCredentials(newUsername: String, newPassword: String, doneMsg: String) {
        val panel = activePanel() ?: return
        if (newUsername.isBlank() || newPassword.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.updatePanelUser(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                newUsername = newUsername,
                newPassword = newPassword,
            )
            if (result is Result.Success) {
                // Persist new credentials locally so further calls stop using the stale pair.
                val draft = PanelDraft(
                    name = panel.name,
                    baseUrl = panel.baseUrl,
                    login = newUsername,
                    password = newPassword,
                    tlsMode = panel.tlsMode,
                    apiToken = panel.apiToken,
                    twoFactorEnabled = panel.twoFactorEnabled,
                )
                repository.update(panel.id, draft)
                _snackbarMessage.value = doneMsg
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
            _isBusy.value = false
        }
    }

    fun restartPanel(restartingMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.restartPanel(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
            )
            _isBusy.value = false
            _snackbarMessage.value = when (result) {
                is Result.Success -> restartingMsg
                is Result.Failure -> result.error.toString()
            }
        }
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private suspend fun loadSettings(panel: Panel) {
        val result = xuiClient.fetchAllSettings(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> PanelSetupUiState.Content(
                panel = panel,
                raw = result.data,
                flags = extractSubFlags(result.data),
            )
            is Result.Failure -> PanelSetupUiState.Error(panel, result.error)
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is PanelSetupUiState.Content -> s.panel
        is PanelSetupUiState.Loading -> s.panel
        is PanelSetupUiState.Error -> s.panel
        PanelSetupUiState.NoActivePanel -> null
    }

    private fun extractSubFlags(obj: JsonObject) = SubFlags(
        enable = obj["subEnable"]?.jsonPrimitive?.boolean ?: false,
        jsonEnable = obj["subJsonEnable"]?.jsonPrimitive?.boolean ?: false,
        clashEnable = obj["subClashEnable"]?.jsonPrimitive?.boolean ?: false,
        uri = obj["subURI"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        jsonUri = obj["subJsonURI"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        clashUri = obj["subClashURI"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )

    private fun patchSubFields(original: JsonObject, flags: SubFlags): JsonObject {
        val patched = original.toMutableMap()
        patched["subEnable"] = JsonPrimitive(flags.enable)
        patched["subJsonEnable"] = JsonPrimitive(flags.jsonEnable)
        patched["subClashEnable"] = JsonPrimitive(flags.clashEnable)
        patched["subURI"] = JsonPrimitive(flags.uri)
        patched["subJsonURI"] = JsonPrimitive(flags.jsonUri)
        patched["subClashURI"] = JsonPrimitive(flags.clashUri)
        return JsonObject(patched)
    }
}
