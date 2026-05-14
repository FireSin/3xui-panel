package com.firesin.xuipanel.feature.nodes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodeDetailViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val nodeId: Int = checkNotNull(savedStateHandle["nodeId"])

    private val _uiState = MutableStateFlow<NodeDetailUiState>(NodeDetailUiState.Loading)
    val uiState: StateFlow<NodeDetailUiState> = _uiState

    private var activePanel: Panel? = null

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                activePanel = panel
                if (panel == null) {
                    _uiState.value = NodeDetailUiState.Error("No active panel")
                } else {
                    load(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectMetric(metric: NodeHistoryMetric) {
        val current = _uiState.value as? NodeDetailUiState.Content ?: return
        _uiState.value = current.copy(selectedMetric = metric)
    }

    fun refresh() {
        val panel = activePanel ?: return
        viewModelScope.launch { load(panel) }
    }

    private suspend fun load(panel: Panel) {
        _uiState.value = NodeDetailUiState.Loading
        val nodeResult = xuiClient.fetchNode(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            auth = panel.toAuth(),
            tls = panel.toPanelTls(),
            id = nodeId,
        )
        val node = when (nodeResult) {
            is Result.Success -> nodeResult.data
            is Result.Failure -> {
                _uiState.value = NodeDetailUiState.Error(nodeResult.error.toString())
                return
            }
        }

        _uiState.value = NodeDetailUiState.Content(
            node = node,
            histories = emptyMap(),
            isHistoryLoading = true,
            selectedMetric = NodeHistoryMetric.CPU,
        )

        val histories = fetchAllHistories(panel)

        val current = _uiState.value as? NodeDetailUiState.Content ?: return
        _uiState.value = current.copy(histories = histories, isHistoryLoading = false)
    }

    private suspend fun fetchAllHistories(panel: Panel): Map<NodeHistoryMetric, List<ServerHistoryPointDto>> =
        coroutineScope {
            val deferreds = NodeHistoryMetric.entries.associateWith { metric ->
                async {
                    val result = xuiClient.fetchNodeHistory(
                        panelId = panel.id,
                        baseUrl = panel.baseUrl,
                        auth = panel.toAuth(),
                        tls = panel.toPanelTls(),
                        nodeId = nodeId,
                        metric = metric.apiKey,
                        bucket = HISTORY_BUCKET_SEC,
                    )
                    (result as? Result.Success)?.data.orEmpty()
                }
            }
            deferreds.mapValues { (_, deferred) -> deferred.await() }
        }

    private companion object {
        /** Bucket 120s × ~180 points = ~6h window, matching dashboard. */
        const val HISTORY_BUCKET_SEC = 120
    }
}
