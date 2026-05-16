package com.firesin.xuipanel.feature.settings.xraytemplate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

data class XrayTemplateState(
    val panel: Panel? = null,
    val pretty: String = "",
    val outboundTestUrl: String = "",
    val error: DomainError? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class XrayTemplateViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(XrayTemplateState())
    val state: StateFlow<XrayTemplateState> = _state

    private val prettyJson = Json { prettyPrint = true }

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                _state.value = XrayTemplateState(panel = panel)
                if (panel != null) load(panel)
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = _state.value.panel ?: return
        viewModelScope.launch { load(panel) }
    }

    private suspend fun load(panel: Panel) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        val result = xuiClient.fetchXrayTemplate(
            panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(),
        )
        _state.value = when (result) {
            is Result.Success -> {
                val pretty = runCatching {
                    prettyJson.encodeToString(JsonElement.serializer(), result.data.xraySetting)
                }.getOrDefault("{}")
                _state.value.copy(
                    isLoading = false,
                    pretty = pretty,
                    outboundTestUrl = result.data.outboundTestUrl,
                )
            }
            is Result.Failure -> _state.value.copy(isLoading = false, error = result.error)
        }
    }
}
