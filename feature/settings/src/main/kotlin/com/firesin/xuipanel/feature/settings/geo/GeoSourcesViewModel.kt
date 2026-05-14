package com.firesin.xuipanel.feature.settings.geo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.AddCustomGeoRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GeoSourcesViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GeoSourcesUiState>(GeoSourcesUiState.NoActivePanel)
    val uiState: StateFlow<GeoSourcesUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    private val _aliases = MutableStateFlow<List<String>>(emptyList())
    val aliases: StateFlow<List<String>> = _aliases

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = GeoSourcesUiState.NoActivePanel
                    _aliases.value = emptyList()
                } else {
                    _uiState.value = GeoSourcesUiState.Loading(panel)
                    loadList(panel)
                    loadAliases(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            loadList(panel)
            _isRefreshing.value = false
        }
    }

    fun add(type: String, alias: String, url: String, onDone: () -> Unit = {}) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.addCustomGeo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                body = AddCustomGeoRequestDto(type = type, alias = alias, url = url),
            )
            if (result is Result.Success) {
                onDone()
                loadList(panel)
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun update(id: Int, type: String, alias: String, url: String, onDone: () -> Unit = {}) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.updateCustomGeo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
                body = AddCustomGeoRequestDto(type = type, alias = alias, url = url),
            )
            if (result is Result.Success) {
                onDone()
                loadList(panel)
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun delete(id: Int, deletedMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteCustomGeo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
            )
            if (result is Result.Success) {
                _snackbarMessage.value = deletedMsg
                loadList(panel)
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun download(id: Int, downloadedMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.downloadCustomGeo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
            )
            if (result is Result.Success) {
                _snackbarMessage.value = downloadedMsg
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun updateAll(updateAllDoneMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.updateAllCustomGeo(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            if (result is Result.Success) {
                _snackbarMessage.value = updateAllDoneMsg
                loadList(panel)
            } else if (result is Result.Failure) {
                _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private suspend fun loadList(panel: Panel) {
        val result = xuiClient.fetchCustomGeoList(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> GeoSourcesUiState.Content(panel, result.data)
            is Result.Failure -> GeoSourcesUiState.Error(panel, result.error)
        }
    }

    private suspend fun loadAliases(panel: Panel) {
        val result = xuiClient.fetchCustomGeoAliases(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
        )
        if (result is Result.Success) {
            _aliases.value = result.data
        }
        // On failure — silently keep empty list; aliases section stays hidden
    }

    private fun activePanel(): Panel? =
        when (val s = _uiState.value) {
            is GeoSourcesUiState.Content -> s.panel
            is GeoSourcesUiState.Loading -> s.panel
            is GeoSourcesUiState.Error -> s.panel
            GeoSourcesUiState.NoActivePanel -> null
        }
}
