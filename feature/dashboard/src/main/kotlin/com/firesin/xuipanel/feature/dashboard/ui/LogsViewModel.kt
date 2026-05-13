package com.firesin.xuipanel.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
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

enum class LogsSource { PANEL, XRAY }

sealed class LogsUiState {
    data object NoActivePanel : LogsUiState()
    data class Loading(val panel: Panel) : LogsUiState()
    data class Content(val panel: Panel, val lines: List<String>) : LogsUiState()
    data class Error(val panel: Panel, val error: DomainError) : LogsUiState()
}

private const val DEFAULT_COUNT = 200

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LogsUiState>(LogsUiState.NoActivePanel)
    val uiState: StateFlow<LogsUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _source = MutableStateFlow(LogsSource.PANEL)
    val source: StateFlow<LogsSource> = _source

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = LogsUiState.NoActivePanel
                } else {
                    _uiState.value = LogsUiState.Loading(panel)
                    load(panel, _source.value)
                }
            }
            .launchIn(viewModelScope)
    }

    fun setSource(source: LogsSource) {
        if (_source.value == source) return
        _source.value = source
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _uiState.value = LogsUiState.Loading(panel)
            load(panel, source)
        }
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            load(panel, _source.value)
            _isRefreshing.value = false
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is LogsUiState.Content -> s.panel
        is LogsUiState.Error -> s.panel
        is LogsUiState.Loading -> s.panel
        is LogsUiState.NoActivePanel -> null
    }

    private suspend fun load(panel: Panel, source: LogsSource) {
        val result = when (source) {
            LogsSource.PANEL -> xuiClient.fetchPanelLogs(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                count = DEFAULT_COUNT,
            )
            LogsSource.XRAY -> xuiClient.fetchXrayLogs(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                count = DEFAULT_COUNT,
            )
        }
        _uiState.value = when (result) {
            is Result.Success -> LogsUiState.Content(panel, result.data)
            is Result.Failure -> LogsUiState.Error(panel, result.error)
        }
    }
}
