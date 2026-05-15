package com.firesin.xuipanel.feature.clients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.WsUiEventBus
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.core.xui.dto.ClientTrafficDto
import com.firesin.xuipanel.core.xui.dto.ClientsJson
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.urlKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ClientsUiState {
    data object NoActivePanel : ClientsUiState()
    data class Loading(val panel: Panel) : ClientsUiState()
    data class Content(
        val panel: Panel,
        val inbounds: List<InboundDto>,
        val selectedInboundId: Int?,
        val clients: List<ClientConfig>,
        /** Per-email live traffic stats from the selected inbound (used/up/down/total). */
        val clientStats: Map<String, ClientStatDto> = emptyMap(),
        /** Emails reported as currently online by the panel. */
        val onlineEmails: Set<String> = emptySet(),
        /** False when the /onlines endpoint failed — UI hides online dots. */
        val onlinesAvailable: Boolean = false,
        /** Map email → last-seen unix timestamp (seconds). Empty when the endpoint failed. */
        val lastOnline: Map<String, Long> = emptyMap(),
    ) : ClientsUiState()
    data class Error(val panel: Panel, val error: DomainError) : ClientsUiState()
}

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
    private val wsUiEvents: WsUiEventBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClientsUiState>(ClientsUiState.NoActivePanel)
    val uiState: StateFlow<ClientsUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<DomainError?>(null)
    val errorMessage: StateFlow<DomainError?> = _errorMessage

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                if (panel == null) {
                    _uiState.value = ClientsUiState.NoActivePanel
                } else {
                    _uiState.value = ClientsUiState.Loading(panel)
                    fetchInbounds(panel, selectedInboundId = null)
                }
            }
            .launchIn(viewModelScope)

        // Panel-pushed `invalidate { resource: "clients"|"inbounds" }` ⇒ refresh.
        // The clients screen lives inside inbounds in the API model, so either resource applies.
        wsUiEvents.invalidations
            .onEach { resource ->
                if (resource.equals("clients", ignoreCase = true) ||
                    resource.equals("inbounds", ignoreCase = true)
                ) {
                    val panel = activePanel() ?: return@onEach
                    val currentId = (_uiState.value as? ClientsUiState.Content)?.selectedInboundId
                    fetchInbounds(panel, selectedInboundId = currentId)
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectInbound(inboundId: Int) {
        val current = _uiState.value as? ClientsUiState.Content ?: return
        _uiState.value = current.copy(
            selectedInboundId = inboundId,
            clients = parseClientsFor(current.inbounds, inboundId),
            clientStats = statsFor(current.inbounds, inboundId),
        )
    }

    fun refresh() {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            val currentId = (_uiState.value as? ClientsUiState.Content)?.selectedInboundId
            fetchInbounds(panel, selectedInboundId = currentId)
            _isRefreshing.value = false
        }
    }

    fun addClient(inboundId: Int, client: ClientConfig) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.addClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
                client = client,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun updateClient(inboundId: Int, clientKey: String, newClient: ClientConfig) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.updateClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
                clientKey = clientKey,
                client = newClient,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun deleteClient(inboundId: Int, client: ClientConfig) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteClient(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
                clientKey = client.urlKey,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun deleteDepletedClients(inboundId: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteDepletedClients(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun resetAllClientTraffics(inboundId: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.resetAllClientTraffics(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun deleteInbound(inboundId: Int) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.deleteInbound(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                id = inboundId,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = null)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    fun resetTraffic(inboundId: Int, client: ClientConfig) {
        val panel = activePanel() ?: return
        viewModelScope.launch {
            val result = xuiClient.resetClientTraffic(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                inboundId = inboundId,
                email = client.email,
            )
            when (result) {
                is Result.Success -> fetchInbounds(panel, selectedInboundId = inboundId)
                is Result.Failure -> _errorMessage.value = result.error
            }
        }
    }

    sealed class ClientIpsState {
        data object Idle : ClientIpsState()
        data object Loading : ClientIpsState()
        data class Loaded(val ips: List<String>) : ClientIpsState()
        data class Error(val error: DomainError) : ClientIpsState()
    }

    private val _clientIpsState = MutableStateFlow<ClientIpsState>(ClientIpsState.Idle)
    val clientIpsState: StateFlow<ClientIpsState> = _clientIpsState

    fun loadClientIps(email: String) {
        val panel = activePanel() ?: return
        if (email.isBlank()) return
        viewModelScope.launch {
            _clientIpsState.value = ClientIpsState.Loading
            val result = xuiClient.fetchClientIps(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                email = email,
            )
            _clientIpsState.value = when (result) {
                is Result.Success -> ClientIpsState.Loaded(result.data)
                is Result.Failure -> ClientIpsState.Error(result.error)
            }
        }
    }

    fun clearClientIps(email: String) {
        val panel = activePanel() ?: return
        if (email.isBlank()) return
        viewModelScope.launch {
            _clientIpsState.value = ClientIpsState.Loading
            val result = xuiClient.clearClientIps(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                email = email,
            )
            when (result) {
                is Result.Success -> loadClientIps(email)
                is Result.Failure -> _clientIpsState.value = ClientIpsState.Error(result.error)
            }
        }
    }

    fun resetClientIpsState() {
        _clientIpsState.value = ClientIpsState.Idle
    }

    sealed class SubLinksState {
        data object Idle : SubLinksState()
        data object Loading : SubLinksState()
        data class Loaded(val links: List<String>) : SubLinksState()
        data class Error(val error: DomainError) : SubLinksState()
    }

    private val _subLinksState = MutableStateFlow<SubLinksState>(SubLinksState.Idle)
    val subLinksState: StateFlow<SubLinksState> = _subLinksState

    fun loadSubLinks(subId: String) {
        val panel = activePanel() ?: return
        if (subId.isBlank()) return
        viewModelScope.launch {
            _subLinksState.value = SubLinksState.Loading
            val result = xuiClient.fetchSubLinks(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                subId = subId,
            )
            _subLinksState.value = when (result) {
                is Result.Success -> SubLinksState.Loaded(result.data)
                is Result.Failure -> SubLinksState.Error(result.error)
            }
        }
    }

    fun resetSubLinksState() {
        _subLinksState.value = SubLinksState.Idle
    }

    // ---- Client traffic ----

    sealed class TrafficState {
        data object Idle : TrafficState()
        data object Loading : TrafficState()
        data class Loaded(val traffic: ClientTrafficDto) : TrafficState()
        data object NoData : TrafficState()
    }

    private val _trafficState = MutableStateFlow<TrafficState>(TrafficState.Idle)
    val trafficState: StateFlow<TrafficState> = _trafficState

    fun loadClientTraffic(email: String) {
        val panel = activePanel() ?: return
        if (email.isBlank()) return
        viewModelScope.launch {
            _trafficState.value = TrafficState.Loading
            val result = xuiClient.fetchClientTrafficsByEmail(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
                email = email,
            )
            _trafficState.value = when (result) {
                is Result.Success -> TrafficState.Loaded(result.data)
                is Result.Failure -> TrafficState.NoData
            }
        }
    }

    fun resetTrafficState() {
        _trafficState.value = TrafficState.Idle
    }

    fun errorShown() {
        _errorMessage.value = null
    }

    private fun activePanel(): Panel? = when (val s = _uiState.value) {
        is ClientsUiState.Content -> s.panel
        is ClientsUiState.Error -> s.panel
        is ClientsUiState.Loading -> s.panel
        is ClientsUiState.NoActivePanel -> null
    }

    private suspend fun fetchInbounds(panel: Panel, selectedInboundId: Int?) {
        val (inboundsResult, onlinesResult, lastOnlineResult) = coroutineScope {
            val inboundsDeferred = async {
                xuiClient.fetchInbounds(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                )
            }
            val onlinesDeferred = async {
                xuiClient.fetchOnlines(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                )
            }
            val lastOnlineDeferred = async {
                xuiClient.fetchLastOnline(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                )
            }
            Triple(inboundsDeferred.await(), onlinesDeferred.await(), lastOnlineDeferred.await())
        }
        _uiState.value = when (inboundsResult) {
            is Result.Success -> {
                val inbounds = inboundsResult.data
                val resolvedId = when {
                    selectedInboundId != null && inbounds.any { it.id == selectedInboundId } -> selectedInboundId
                    inbounds.isNotEmpty() -> inbounds.first().id
                    else -> null
                }
                val (onlineEmails, onlinesAvailable) = when (onlinesResult) {
                    is Result.Success -> onlinesResult.data to true
                    is Result.Failure -> emptySet<String>() to false
                }
                val lastOnline = (lastOnlineResult as? Result.Success)?.data.orEmpty()
                ClientsUiState.Content(
                    panel = panel,
                    inbounds = inbounds,
                    selectedInboundId = resolvedId,
                    clients = parseClientsFor(inbounds, resolvedId),
                    clientStats = statsFor(inbounds, resolvedId),
                    onlineEmails = onlineEmails,
                    onlinesAvailable = onlinesAvailable,
                    lastOnline = lastOnline,
                )
            }
            is Result.Failure -> ClientsUiState.Error(panel, inboundsResult.error)
        }
    }

    private fun parseClientsFor(inbounds: List<InboundDto>, inboundId: Int?): List<ClientConfig> {
        val inbound = inbounds.firstOrNull { it.id == inboundId } ?: return emptyList()
        return if (inbound.protocol.isSupportedProtocol()) {
            runCatching { ClientsJson.parse(inbound.protocol, inbound.settings) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    private fun statsFor(inbounds: List<InboundDto>, inboundId: Int?): Map<String, ClientStatDto> {
        val inbound = inbounds.firstOrNull { it.id == inboundId } ?: return emptyMap()
        return inbound.clientStats.orEmpty().associateBy { it.email }
    }
}

/** Protocols supported for CRUD. */
fun String.isSupportedProtocol(): Boolean =
    lowercase() in setOf("vmess", "vless", "shadowsocks", "trojan", "hysteria")
