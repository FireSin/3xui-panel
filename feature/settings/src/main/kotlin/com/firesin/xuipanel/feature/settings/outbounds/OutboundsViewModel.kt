package com.firesin.xuipanel.feature.settings.outbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

@HiltViewModel
class OutboundsViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OutboundsUiState>(OutboundsUiState.NoActivePanel)
    val uiState: StateFlow<OutboundsUiState> = _uiState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _xrayResult = MutableStateFlow<String?>(null)
    val xrayResult: StateFlow<String?> = _xrayResult

    /** Result of last testOutbound call, shown in a dialog. */
    private val _testResult = MutableStateFlow<TestOutboundUiResult?>(null)
    val testResult: StateFlow<TestOutboundUiResult?> = _testResult

    data class TestOutboundUiResult(
        val tag: String,
        val success: Boolean,
        val delayMs: Long,
        val error: String,
        val mode: String,
    )

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = OutboundsUiState.NoActivePanel
                } else {
                    _uiState.value = OutboundsUiState.Loading(panel)
                    loadTraffic(panel)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            loadTraffic(panel)
            _isBusy.value = false
        }
    }

    fun resetTag(tag: String, doneMsg: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.resetOutboundTraffic(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                tag = tag,
            )
            when (result) {
                is Result.Success -> {
                    _snackbarMessage.value = doneMsg
                    loadTraffic(panel)
                }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
            _isBusy.value = false
        }
    }

    fun loadXrayResult() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.fetchXrayResult(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> _xrayResult.value = result.data.ifBlank { "—" }
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun dismissXrayResult() {
        _xrayResult.value = null
    }

    fun testTag(tag: String) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = xuiClient.testOutboundByTag(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                username = panel.login,
                password = panel.password,
                tls = panel.toPanelTls(),
                tag = tag,
            )
            _isBusy.value = false
            when (result) {
                is Result.Success -> _testResult.value = TestOutboundUiResult(
                    tag = tag,
                    success = result.data.success,
                    delayMs = result.data.delay,
                    error = result.data.error,
                    mode = result.data.mode,
                )
                is Result.Failure -> _snackbarMessage.value = result.error.toString()
            }
        }
    }

    fun dismissTestResult() {
        _testResult.value = null
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }

    private suspend fun loadTraffic(panel: Panel) {
        val result = xuiClient.fetchOutboundsTraffic(
            panelId = panel.id,
            baseUrl = panel.baseUrl,
            username = panel.login,
            password = panel.password,
            tls = panel.toPanelTls(),
        )
        _uiState.value = when (result) {
            is Result.Success -> OutboundsUiState.Content(panel, result.data)
            is Result.Failure -> OutboundsUiState.Error(panel, result.error)
        }
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is OutboundsUiState.Content -> s.panel
        is OutboundsUiState.Loading -> s.panel
        is OutboundsUiState.Error -> s.panel
        OutboundsUiState.NoActivePanel -> null
    }
}
