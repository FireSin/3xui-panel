package com.firesin.xuipanel.feature.settings.xraymetrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.XrayMetricsStateDto
import com.firesin.xuipanel.core.xui.dto.XrayObservatoryEntryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class XrayMetricsScreenState(
    val panel: Panel? = null,
    val metricsState: XrayMetricsStateDto? = null,
    val observatory: List<XrayObservatoryEntryDto> = emptyList(),
    val error: DomainError? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class XrayMetricsViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(XrayMetricsScreenState())
    val state: StateFlow<XrayMetricsScreenState> = _state

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                _state.value = XrayMetricsScreenState(panel = panel)
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
        val auth = panel.toAuth()
        val tls = panel.toPanelTls()
        val stateResult = xuiClient.fetchXrayMetricsState(panel.id, panel.baseUrl, auth, tls)
        val observatoryResult = xuiClient.fetchXrayObservatory(panel.id, panel.baseUrl, auth, tls)
        _state.value = _state.value.copy(
            isLoading = false,
            metricsState = (stateResult as? Result.Success)?.data,
            observatory = (observatoryResult as? Result.Success)?.data.orEmpty(),
            error = (stateResult as? Result.Failure)?.error,
        )
    }
}
