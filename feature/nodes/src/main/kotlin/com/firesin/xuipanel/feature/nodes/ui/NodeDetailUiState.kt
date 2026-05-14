package com.firesin.xuipanel.feature.nodes.ui

import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto

/** Metrics supported by GET /panel/api/nodes/history/:id/:metric/:bucket. */
enum class NodeHistoryMetric(val apiKey: String) {
    CPU("cpu"),
    MEM("mem"),
    NET_IN("netIn"),
    NET_OUT("netOut"),
    LATENCY("latency"),
    ONLINE("online"),
}

sealed class NodeDetailUiState {
    data object Loading : NodeDetailUiState()
    data class Content(
        val node: NodeDto,
        val histories: Map<NodeHistoryMetric, List<ServerHistoryPointDto>>,
        val isHistoryLoading: Boolean,
        val selectedMetric: NodeHistoryMetric,
    ) : NodeDetailUiState()
    data class Error(val message: String) : NodeDetailUiState()
}
