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
        /** Direct protocol URL (vless://, vmess://, …) — always present. */
        val uri: String,
        /** HTTP subscription URL (`subURI + subId`) — null if subscription is disabled
         *  on the panel or the client has no subId. */
        val subUri: String? = null,
        /** Clash subscription URL (`subClashURI + subId`) — null when disabled. */
        val clashUri: String? = null,
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

                    // Server-first: try canonical URL from panel, fallback to local builder.
                    val serverUrl: String? = if (client.email.isNotBlank()) {
                        val linksResult = xuiClient.fetchClientLinks(
                            panelId = panel.id,
                            baseUrl = panel.baseUrl,
                            auth = panel.toAuth(),
                            tls = panel.toPanelTls(),
                            inboundId = inboundId,
                            email = client.email,
                        )
                        (linksResult as? Result.Success)?.data?.firstOrNull()
                    } else {
                        null
                    }

                    // Subscription / Clash URLs are computed from panel settings
                    // (`subURI + subId`, `subClashURI + subId`). The endpoint sits on
                    // `/panel/setting/*` which requires a cookie session + CSRF, so we
                    // pass login/password directly — Bearer-only panels can't reach it
                    // and gracefully degrade to «direct link only».
                    val (subUri, clashUri) = run {
                        if (client.subId.isBlank()) return@run null to null
                        val settingsResult = xuiClient.fetchPanelSettings(
                            panelId = panel.id,
                            baseUrl = panel.baseUrl,
                            username = panel.login,
                            password = panel.password,
                            tls = panel.toPanelTls(),
                        )
                        val settings = (settingsResult as? Result.Success)?.data
                            ?: return@run null to null
                        val sub = if (settings.subEnable && settings.subUri.isNotBlank()) {
                            settings.subUri + client.subId
                        } else null
                        val clash =
                            if (settings.subClashEnable && settings.subClashUri.isNotBlank()) {
                                settings.subClashUri + client.subId
                            } else null
                        sub to clash
                    }

                    if (serverUrl != null) {
                        _uiState.value = ShareUiState.Content(
                            uri = serverUrl,
                            subUri = subUri,
                            clashUri = clashUri,
                            client = client,
                            inbound = inbound,
                        )
                    } else {
                        val uriResult = ClientUri.build(client, inbound, host)
                        _uiState.value = when (uriResult) {
                            is Result.Success -> ShareUiState.Content(
                                uri = uriResult.data,
                                subUri = subUri,
                                clashUri = clashUri,
                                client = client,
                                inbound = inbound,
                            )
                            is Result.Failure -> ShareUiState.Error(shareError = uriResult.error)
                        }
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
