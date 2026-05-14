package com.firesin.xuipanel.feature.settings.geo

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.CustomGeoResourceDto

sealed interface GeoSourcesUiState {
    data object NoActivePanel : GeoSourcesUiState
    data class Loading(val panel: Panel) : GeoSourcesUiState
    data class Content(
        val panel: Panel,
        val items: List<CustomGeoResourceDto>,
    ) : GeoSourcesUiState
    data class Error(val panel: Panel, val error: DomainError) : GeoSourcesUiState
}
