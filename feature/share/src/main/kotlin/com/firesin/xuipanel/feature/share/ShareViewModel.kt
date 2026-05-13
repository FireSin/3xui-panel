package com.firesin.xuipanel.feature.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.ClientsJson
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.urlKey
import com.firesin.xuipanel.core.xui.share.ClientUri
import com.firesin.xuipanel.core.xui.share.ShareError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ShareUiState {
    data object Loading : ShareUiState()
    data class Content(
        val uri: String,
        val client: ClientConfig,
        val inbound: InboundDto,
    ) : ShareUiState()
    data class Error(val shareError: ShareError? = null, val domainError: DomainError? = null) :
        ShareUiState()
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
) : ViewModel() {

    private val inboundId: Int = checkNotNull(savedStateHandle[ARG_INBOUND_ID])
    // nav-compose StringType already percent-decodes path args, so SavedStateHandle
    // gives us the raw urlKey (UUID or SS email) ready to compare.
    private val clientKey: String = checkNotNull(savedStateHandle[ARG_CLIENT_KEY])

    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Loading)
    val uiState: StateFlow<ShareUiState> = _uiState

    init {
        load()
    }

    fun retry() {
        _uiState.value = ShareUiState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val panel = repository.observeActive().first()
            if (panel == null) {
                _uiState.value = ShareUiState.Error(
                    domainError = DomainError.Unexpected(IllegalStateException("No active panel")),
                )
                return@launch
            }

            val fetchResult = xuiClient.fetchInbounds(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )

            when (fetchResult) {
                is Result.Failure -> {
                    _uiState.value = ShareUiState.Error(domainError = fetchResult.error)
                    return@launch
                }
                is Result.Success -> {
                    val inbound = fetchResult.data.firstOrNull { it.id == inboundId }
                    if (inbound == null) {
                        _uiState.value = ShareUiState.Error(
                            shareError = ShareError.UnsupportedProtocol("inbound#$inboundId not found"),
                        )
                        return@launch
                    }

                    val clients = runCatching {
                        ClientsJson.parse(inbound.protocol, inbound.settings)
                    }.getOrDefault(emptyList())

                    val client = clients.firstOrNull { it.urlKey == clientKey }
                    if (client == null) {
                        _uiState.value = ShareUiState.Error(
                            shareError = ShareError.UnsupportedProtocol("client $clientKey not found"),
                        )
                        return@launch
                    }

                    // java.net.URI (not android.net.Uri) so unit tests don't hit the
                    // un-mocked Android stub. Falls back to the raw baseUrl on parse failure.
                    val host = runCatching { java.net.URI(panel.baseUrl).host }
                        .getOrNull() ?: panel.baseUrl
                    val uriResult = ClientUri.build(client, inbound, host)
                    _uiState.value = when (uriResult) {
                        is Result.Success -> ShareUiState.Content(
                            uri = uriResult.data,
                            client = client,
                            inbound = inbound,
                        )
                        is Result.Failure -> ShareUiState.Error(shareError = uriResult.error)
                    }
                }
            }
        }
    }

    companion object {
        const val ARG_INBOUND_ID = "inboundId"
        const val ARG_CLIENT_KEY = "clientKey"
    }
}
