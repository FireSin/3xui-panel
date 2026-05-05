package com.firesin.xuipanel.feature.stats

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.InboundDto

sealed class StatsUiState {
    data object NoActivePanel : StatsUiState()
    data object Loading : StatsUiState()
    data class Error(val error: DomainError) : StatsUiState()
    data class Content(
        val panel: Panel,
        val summary: ServerSummary,
        val inbounds: List<InboundDto>,
        val expandedIds: Set<Int>,
        val onlineEmails: Set<String>,
        val onlinesAvailable: Boolean,
    ) : StatsUiState()
}

data class ServerSummary(
    val totalUp: Long,
    val totalDown: Long,
    val inboundCount: Int,
    val activeClientCount: Int,
)
