package com.firesin.xuipanel.feature.nodes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.NodeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodesListViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NodesUiState>(NodesUiState.NoActivePanel)
    val uiState: StateFlow<NodesUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<DomainError?>(null)
    val errorMessage: StateFlow<DomainError?> = _errorMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = NodesUiState.NoActivePanel
                } else {
                    _uiState.value = NodesUiState.Loading(panel)
                    fetchNodes(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchNodes(panel)
            _isRefreshing.value = false
        }
    }

    fun setEnable(id: Int, enable: Boolean) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.setNodeEnabled(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
                enable = enable,
            )
            when (result) {
                is Result.Success -> fetchNodes(panel)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun probe(id: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.probeNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
            )
            when (result) {
                is Result.Success -> fetchNodes(panel)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun delete(id: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteNode(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = id,
            )
            when (result) {
                is Result.Success -> fetchNodes(panel)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is NodesUiState.Content -> s.panel
        is NodesUiState.Error -> s.panel
        is NodesUiState.Loading -> s.panel
        is NodesUiState.NoActivePanel -> null
    }

    private suspend fun fetchNodes(panel: Panel) {
        val result = xuiClient.fetchNodes(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> NodesUiState.Content(panel, result.data)
            is Result.Failure -> NodesUiState.Error(panel, result.error)
        }
    }
}
