package com.firesin.xuipanel.feature.settings.outbounds

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.OutboundTrafficDto

sealed class OutboundsUiState {
    data object NoActivePanel : OutboundsUiState()
    data class Loading(val panel: Panel) : OutboundsUiState()
    data class Content(val panel: Panel, val outbounds: List<OutboundTrafficDto>) : OutboundsUiState()
    data class Error(val panel: Panel, val error: DomainError) : OutboundsUiState()
}
