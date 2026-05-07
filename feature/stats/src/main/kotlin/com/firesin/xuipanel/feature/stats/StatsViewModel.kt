package com.firesin.xuipanel.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.InboundDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

private val logger = Logger.getLogger("StatsViewModel")

enum class ChartRange(val days: Long) { D7(7L), D30(30L), D90(90L) }

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val xuiClient: XuiClient,
    private val historyRepository: TrafficHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _range = MutableStateFlow(ChartRange.D7)
    val range: StateFlow<ChartRange> = _range

    fun setRange(range: ChartRange) { _range.value = range }

    /**
     * Returns a Flow of daily points for the given inbound, reacting to range changes.
     * panelId is derived from current uiState — call only when state is Content.
     */
    fun chartFlow(panelId: String, inboundId: Int): Flow<List<DailyPoint>> =
        _range.flatMapLatest { chartRange ->
            val msPerDay = 86_400_000L
            val now = System.currentTimeMillis()
            val todayMidnight = (now / msPerDay) * msPerDay
            val fromEpoch = todayMidnight - chartRange.days * msPerDay
            val toEpoch = todayMidnight + msPerDay
            historyRepository.observeInboundDaily(panelId, inboundId, fromEpoch, toEpoch)
        }

    init {
        viewModelScope.launch {
            panelRepository.observeActive()
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { panel ->
                    if (panel == null) {
                        _uiState.value = StatsUiState.NoActivePanel
                    } else {
                        _uiState.value = StatsUiState.Loading
                        load(panel)
                    }
                }
        }
    }

    fun refresh() {
        val current = _uiState.value
        val (panel, preservedExpanded) = when (current) {
            is StatsUiState.Content -> Pair(current.panel, current.expandedIds)
            else -> {
                // Error or other non-content states: do a full retry
                retry()
                return
            }
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            load(panel, preservedExpanded)
            _isRefreshing.value = false
        }
    }

    fun toggleExpanded(inboundId: Int) {
        val current = _uiState.value as? StatsUiState.Content ?: return
        val newIds = if (inboundId in current.expandedIds) {
            current.expandedIds - inboundId
        } else {
            current.expandedIds + inboundId
        }
        _uiState.value = current.copy(expandedIds = newIds)
    }

    fun retry() {
        viewModelScope.launch {
            val panel = panelRepository.observeActive().first()
            if (panel == null) {
                _uiState.value = StatsUiState.NoActivePanel
            } else {
                _uiState.value = StatsUiState.Loading
                load(panel)
            }
        }
    }

    private suspend fun load(panel: Panel, preserveExpandedIds: Set<Int> = emptySet()) {
        coroutineScope {
            val inboundsDeferred = async {
                xuiClient.fetchInbounds(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    username = panel.login,
                    password = panel.password,
                    tls = panel.toPanelTls(),
                )
            }
            val onlinesDeferred = async {
                xuiClient.fetchOnlines(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    username = panel.login,
                    password = panel.password,
                    tls = panel.toPanelTls(),
                )
            }

            val inboundsResult = inboundsDeferred.await()
            val onlinesResult = onlinesDeferred.await()

            when (inboundsResult) {
                is Result.Failure -> {
                    _uiState.value = StatsUiState.Error(inboundsResult.error)
                }
                is Result.Success -> {
                    val inbounds = inboundsResult.data
                    val (onlineEmails, onlinesAvailable) = when (onlinesResult) {
                        is Result.Success -> Pair(onlinesResult.data, true)
                        is Result.Failure -> {
                            logger.warning("fetchOnlines failed: ${onlinesResult.error::class.simpleName}")
                            Pair(emptySet<String>(), false)
                        }
                    }
                    _uiState.value = StatsUiState.Content(
                        panel = panel,
                        summary = buildSummary(inbounds),
                        inbounds = inbounds,
                        expandedIds = preserveExpandedIds,
                        onlineEmails = onlineEmails,
                        onlinesAvailable = onlinesAvailable,
                    )
                }
            }
        }
    }

    private fun buildSummary(inbounds: List<InboundDto>): ServerSummary {
        var totalUp = 0L
        var totalDown = 0L
        var activeClientCount = 0
        for (inbound in inbounds) {
            totalUp += inbound.up
            totalDown += inbound.down
            activeClientCount += inbound.clientStats.orEmpty().count { it.enable }
        }
        return ServerSummary(
            totalUp = totalUp,
            totalDown = totalDown,
            inboundCount = inbounds.size,
            activeClientCount = activeClientCount,
        )
    }
}
