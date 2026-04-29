package com.firesin.xuipanel.feature.inbounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.InboundDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InboundsUiState {
    data object NoActivePanel : InboundsUiState()
    data class Loading(val panel: Panel) : InboundsUiState()
    data class Content(val panel: Panel, val inbounds: List<InboundDto>) : InboundsUiState()
    data class Error(val panel: Panel, val error: DomainError) : InboundsUiState()
}

@HiltViewModel
class InboundsViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboundsUiState>(InboundsUiState.NoActivePanel)
    val uiState: StateFlow<InboundsUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<DomainError?>(null)
    val errorMessage: StateFlow<DomainError?> = _errorMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = InboundsUiState.NoActivePanel
                } else {
                    _uiState.value = InboundsUiState.Loading(panel)
                    fetchInbounds(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchInbounds(panel)
            _isRefreshing.value = false
        }
    }

    fun toggle(id: Int, enable: Boolean) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.setInboundEnabled(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                enabled = enable,
                id = id,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun delete(id: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteInbound(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                id = id,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is InboundsUiState.Content -> s.panel
        is InboundsUiState.Error -> s.panel
        is InboundsUiState.Loading -> s.panel
        is InboundsUiState.NoActivePanel -> null
    }

    private suspend fun fetchInbounds(panel: Panel) {
        val result = xuiClient.fetchInbounds(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> InboundsUiState.Content(panel, result.data)
            is Result.Failure -> InboundsUiState.Error(panel, result.error)
        }
    }
}
