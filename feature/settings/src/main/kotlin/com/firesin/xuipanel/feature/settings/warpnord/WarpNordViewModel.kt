package com.firesin.xuipanel.feature.settings.warpnord

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

data class WarpNordState(
    val panel: Panel? = null,
    val warpData: String = "",
    val nordData: String = "",
    val isBusy: Boolean = false,
)

@HiltViewModel
class WarpNordViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(WarpNordState())
    val state: StateFlow<WarpNordState> = _state

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                _state.value = WarpNordState(panel = panel)
                if (panel != null) refresh()
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        val panel = _state.value.panel ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val warp = xuiClient.warpAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(), "data",
            )
            val nord = xuiClient.nordAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(), "data",
            )
            _state.value = _state.value.copy(
                isBusy = false,
                warpData = (warp as? Result.Success)?.data.orEmpty(),
                nordData = (nord as? Result.Success)?.data.orEmpty(),
            )
        }
    }

    fun setWarpLicense(license: String, doneMsg: String) {
        val panel = _state.value.panel ?: return
        if (license.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = xuiClient.warpAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(),
                action = "license",
                license = license.trim(),
            )
            _state.value = _state.value.copy(isBusy = false)
            _snackbarMessage.value = when (result) {
                is Result.Success -> doneMsg
                is Result.Failure -> result.error.toString()
            }
            if (result is Result.Success) refresh()
        }
    }

    fun deleteWarp(doneMsg: String) {
        val panel = _state.value.panel ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = xuiClient.warpAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(), "del",
            )
            _state.value = _state.value.copy(isBusy = false)
            _snackbarMessage.value = when (result) {
                is Result.Success -> doneMsg
                is Result.Failure -> result.error.toString()
            }
            if (result is Result.Success) refresh()
        }
    }

    fun setNordKey(key: String, doneMsg: String) {
        val panel = _state.value.panel ?: return
        if (key.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = xuiClient.nordAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(),
                action = "setKey",
                key = key.trim(),
            )
            _state.value = _state.value.copy(isBusy = false)
            _snackbarMessage.value = when (result) {
                is Result.Success -> doneMsg
                is Result.Failure -> result.error.toString()
            }
            if (result is Result.Success) refresh()
        }
    }

    fun deleteNord(doneMsg: String) {
        val panel = _state.value.panel ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = xuiClient.nordAction(
                panel.id, panel.baseUrl, panel.login, panel.password, panel.toPanelTls(), "del",
            )
            _state.value = _state.value.copy(isBusy = false)
            _snackbarMessage.value = when (result) {
                is Result.Success -> doneMsg
                is Result.Failure -> result.error.toString()
            }
            if (result is Result.Success) refresh()
        }
    }

    fun snackbarConsumed() {
        _snackbarMessage.value = null
    }
}
