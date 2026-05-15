package com.firesin.xuipanel.feature.settings.panelsetup

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.data.model.Panel
import kotlinx.serialization.json.JsonObject

data class SubFlags(
    val enable: Boolean = false,
    val jsonEnable: Boolean = false,
    val clashEnable: Boolean = false,
    val uri: String = "",
    val jsonUri: String = "",
    val clashUri: String = "",
)

sealed class PanelSetupUiState {
    data object NoActivePanel : PanelSetupUiState()
    data class Loading(val panel: Panel) : PanelSetupUiState()
    data class Content(
        val panel: Panel,
        val raw: JsonObject,
        val flags: SubFlags,
    ) : PanelSetupUiState()
    data class Error(val panel: Panel, val error: DomainError) : PanelSetupUiState()
}
