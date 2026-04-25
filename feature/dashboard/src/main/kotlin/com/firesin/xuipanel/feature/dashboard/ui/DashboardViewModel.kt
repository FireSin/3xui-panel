package com.firesin.xuipanel.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardUiState {
    data object NoActivePanel : DashboardUiState()
    data class Loading(val panel: Panel) : DashboardUiState()
    data class Content(val panel: Panel, val status: ServerStatusDto) : DashboardUiState()
    data class Error(val panel: Panel, val error: DomainError) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.NoActivePanel)
    val uiState: StateFlow<DashboardUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = DashboardUiState.NoActivePanel
                } else {
                    _uiState.value = DashboardUiState.Loading(panel)
                    fetchStatus(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchStatus(panel)
            _isRefreshing.value = false
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is DashboardUiState.Content -> s.panel
        is DashboardUiState.Error -> s.panel
        is DashboardUiState.Loading -> s.panel
        is DashboardUiState.NoActivePanel -> null
    }

    private suspend fun fetchStatus(panel: Panel) {
        val result = xuiClient.fetchServerStatus(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            trustSelfSigned = panel.trustSelfSigned,
        )
        _uiState.value = when (result) {
            is Result.Success -> DashboardUiState.Content(panel, result.data)
            is Result.Failure -> DashboardUiState.Error(panel, result.error)
        }
    }
}
