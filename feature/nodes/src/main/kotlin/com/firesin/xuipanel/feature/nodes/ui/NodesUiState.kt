package com.firesin.xuipanel.feature.nodes.ui

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.NodeDto

sealed class NodesUiState {
    data object NoActivePanel : NodesUiState()
    data class Loading(val panel: Panel) : NodesUiState()
    data class Content(val panel: Panel, val nodes: List<NodeDto>) : NodesUiState()
    data class Error(val panel: Panel, val error: DomainError) : NodesUiState()
}
