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
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.ws.WsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Server-history metrics supported by /panel/api/server/history. */
enum class HistoryMetric(val apiKey: String) {
    CPU("cpu"),
    MEM("mem"),
    // Newer 3x-ui renamed netIn/netOut to netDown (server-side inbound) / netUp (outbound).
    NET_IN("netDown"),
    NET_OUT("netUp"),
    ONLINE("online"),
}

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

    sealed class ActionEvent {
        data object RestartSuccess : ActionEvent()
        data object StopSuccess : ActionEvent()
        data class Failure(val error: DomainError) : ActionEvent()
    }

    private val _actionEvent = MutableStateFlow<ActionEvent?>(null)
    val actionEvent: StateFlow<ActionEvent?> = _actionEvent

    private val _isActionInFlight = MutableStateFlow(false)
    val isActionInFlight: StateFlow<Boolean> = _isActionInFlight

    private val _selectedMetric = MutableStateFlow(HistoryMetric.CPU)
    val selectedMetric: StateFlow<HistoryMetric> = _selectedMetric

    private val _history = MutableStateFlow<List<ServerHistoryPointDto>>(emptyList())
    val history: StateFlow<List<ServerHistoryPointDto>> = _history

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading

    /** All known panels — used by the in-screen panel switcher chip/sheet. */
    val allPanels: StateFlow<List<Panel>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setActivePanel(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }

    /** Live status push via WS (replaces polling). Cancelled when active panel changes. */
    private var wsJob: Job? = null

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                wsJob?.cancel()
                if (panel == null) {
                    _uiState.value = DashboardUiState.NoActivePanel
                    _history.value = emptyList()
                } else {
                    _uiState.value = DashboardUiState.Loading(panel)
                    fetchStatus(panel)
                    fetchHistory(panel, _selectedMetric.value)
                    wsJob = subscribeLiveStatus(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Connects to `<base>/ws` and pushes incoming `status` events into [_uiState].
     * Failures stop the stream silently — initial HTTP fetch already populated the UI,
     * so the user just sees a static snapshot in that case (the next `refresh()` works as before).
     */
    private fun subscribeLiveStatus(panel: Panel): Job = viewModelScope.launch {
        xuiClient.observeWs(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
            .catch { /* ignore — WS auth might fail; the static fetch above is enough */ }
            .collect { event ->
                if (event is WsEvent.Status) {
                    _uiState.value = DashboardUiState.Content(panel, event.server)
                }
            }
    }

    fun selectMetric(metric: HistoryMetric) {
        if (_selectedMetric.value == metric) return
        _selectedMetric.value = metric
        val panel = activePanel() ?: return
        viewModelScope.launch { fetchHistory(panel, metric) }
    }

    private suspend fun fetchHistory(panel: Panel, metric: HistoryMetric) {
        _isHistoryLoading.value = true
        val result = xuiClient.fetchServerHistory(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
            metric = metric.apiKey,
            bucket = HISTORY_BUCKET_SEC,
        )
        _history.value = (result as? Result.Success)?.data.orEmpty()
        _isHistoryLoading.value = false
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchStatus(panel)
            fetchHistory(panel, _selectedMetric.value)
            _isRefreshing.value = false
        }
    }

    fun restartXray() {
        val panel = activePanel() ?: return
        if (_isActionInFlight.value) return
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = xuiClient.restartXray(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            _actionEvent.value = when (result) {
                is Result.Success -> ActionEvent.RestartSuccess
                is Result.Failure -> ActionEvent.Failure(result.error)
            }
            if (result is Result.Success) fetchStatus(panel)
            _isActionInFlight.value = false
        }
    }

    fun stopXray() {
        val panel = activePanel() ?: return
        if (_isActionInFlight.value) return
        viewModelScope.launch {
            _isActionInFlight.value = true
            val result = xuiClient.stopXray(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            _actionEvent.value = when (result) {
                is Result.Success -> ActionEvent.StopSuccess
                is Result.Failure -> ActionEvent.Failure(result.error)
            }
            if (result is Result.Success) fetchStatus(panel)
            _isActionInFlight.value = false
        }
    }

    fun actionEventShown() {
        _actionEvent.value = null
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is DashboardUiState.Content -> s.panel
        is DashboardUiState.Error -> s.panel
        is DashboardUiState.Loading -> s.panel
        is DashboardUiState.NoActivePanel -> null
    }

    private companion object {
        /** Bucket size 120s × ~180 points = ~6h window. Matches the cached server range. */
        const val HISTORY_BUCKET_SEC = 120
    }

    private suspend fun fetchStatus(panel: Panel) {
        val result = xuiClient.fetchServerStatus(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> DashboardUiState.Content(panel, result.data)
            is Result.Failure -> DashboardUiState.Error(panel, result.error)
        }
    }
}
