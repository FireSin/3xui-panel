package com.firesin.xuipanel.feature.settings.xraytemplate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
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
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class XrayTemplateState(
    val panel: Panel? = null,
    val pretty: String = "",
    val xraySetting: JsonObject? = null,
    val outboundTestUrl: String = "",
    val metricsEnabled: Boolean = false,
    val error: DomainError? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null,
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

    fun snackbarConsumed() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    fun enableMetrics() = mutateMetrics(enable = true)
    fun disableMetrics() = mutateMetrics(enable = false)

    private fun mutateMetrics(enable: Boolean) {
        val panel = _state.value.panel ?: return
        val xraySetting = _state.value.xraySetting ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, error = null)
            val patched = if (enable) {
                xuiClient.enableXrayMetrics(xraySetting)
            } else {
                xuiClient.disableXrayMetrics(xraySetting)
            }
            val result = xuiClient.updateXrayTemplate(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(),
                xraySetting = patched,
            )
            when (result) {
                is Result.Success -> {
                    xuiClient.restartXray(panel.id, panel.baseUrl, panel.toAuth(), panel.toPanelTls())
                    _state.value = _state.value.copy(
                        isBusy = false,
                        snackbarMessage = if (enable) "Метрики включены — Xray перезапущен" else "Метрики отключены — Xray перезапущен",
                    )
                    load(panel)
                }
                is Result.Failure -> _state.value = _state.value.copy(isBusy = false, error = result.error)
            }
        }
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
                    xraySetting = result.data.xraySetting,
                    metricsEnabled = xuiClient.isXrayMetricsEnabled(result.data.xraySetting),
                    outboundTestUrl = result.data.outboundTestUrl,
                )
            }
            is Result.Failure -> _state.value.copy(isLoading = false, error = result.error)
        }
    }
}
