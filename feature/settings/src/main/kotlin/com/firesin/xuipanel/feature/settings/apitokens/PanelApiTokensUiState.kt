package com.firesin.xuipanel.feature.settings.apitokens

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.ApiTokenDto

sealed class PanelApiTokensUiState {
    data object NoActivePanel : PanelApiTokensUiState()
    data class Loading(val panel: Panel) : PanelApiTokensUiState()
    data class Content(val panel: Panel, val tokens: List<ApiTokenDto>) : PanelApiTokensUiState()
    data class Error(val panel: Panel, val error: DomainError) : PanelApiTokensUiState()
}
