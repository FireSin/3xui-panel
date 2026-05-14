package com.firesin.xuipanel.feature.inbounds.add.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.draft.InboundDecoder
import com.firesin.xuipanel.core.xui.draft.InboundEncoder
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.util.randomUuid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddInboundViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val xuiClient: XuiClient,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddInboundUiState>(AddInboundUiState.Editing())
    val uiState: StateFlow<AddInboundUiState> = _uiState

    private val _availableNodes = MutableStateFlow<List<NodeDto>>(emptyList())
    /** Enabled nodes available as deploy targets. Empty list → only «Local panel» option shown. */
    val availableNodes: StateFlow<List<NodeDto>> = _availableNodes.asStateFlow()

    private var activePanel: Panel? = null

    init {
        repository.observeActive()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { panel ->
                activePanel = panel
                if (panel != null) loadNodes(panel)
            }
            .launchIn(viewModelScope)

        val inboundId = savedStateHandle.get<Int>("inboundId")
        if (inboundId != null) {
            loadForEdit(inboundId)
        }
    }

    private fun loadNodes(panel: Panel) {
        viewModelScope.launch {
            val result = xuiClient.fetchNodes(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            _availableNodes.value = when (result) {
                is Result.Success -> result.data.filter { it.enable }
                is Result.Failure -> emptyList()
            }
        }
    }

    private fun loadForEdit(inboundId: Int) {
        _uiState.value = AddInboundUiState.Editing(isLoading = true, editingInboundId = inboundId)
        viewModelScope.launch {
            // Wait for active panel (may arrive slightly after init)
            val panel = repository.observeActive().first { it != null } ?: run {
                _uiState.value = AddInboundUiState.Editing(
                    errorMessage = "Нет активной панели",
                    editingInboundId = inboundId,
                )
                return@launch
            }
            when (val result = xuiClient.fetchInbounds(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )) {
                is Result.Success -> {
                    val dto = result.data.find { it.id == inboundId }
                    if (dto == null) {
                        _uiState.value = AddInboundUiState.Editing(
                            errorMessage = "Инбаунд #$inboundId не найден",
                            editingInboundId = inboundId,
                        )
                    } else {
                        val draft = InboundDecoder.decode(dto)
                        val formState = AddInboundFormState.fromInboundDraft(draft)
                        _uiState.value = AddInboundUiState.Editing(
                            formState = formState,
                            isLoading = false,
                            editingInboundId = inboundId,
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.value = AddInboundUiState.Editing(
                        errorMessage = result.error.toUserMessage(),
                        isLoading = false,
                        editingInboundId = inboundId,
                    )
                }
            }
        }
    }

    // ---- form field updaters ----

    private fun updateForm(block: AddInboundFormState.() -> AddInboundFormState) {
        val editing = _uiState.value as? AddInboundUiState.Editing ?: return
        _uiState.value = editing.copy(formState = editing.formState.block())
    }

    fun updateRemark(v: String) = updateForm { copy(remark = v) }
    fun updatePort(v: String) = updateForm { copy(port = v) }
    fun updateNodeId(v: Int?) = updateForm { copy(nodeId = v) }
    fun updateListen(v: String) = updateForm { copy(listen = v) }
    fun updateEnable(v: Boolean) = updateForm { copy(enable = v) }
    fun updateExpiryTime(v: Long) = updateForm { copy(expiryTime = v) }
    fun updateTotalGb(v: String) = updateForm { copy(totalGb = v) }
    fun updateSelectedProtocol(v: ProtocolType) = updateForm { copy(selectedProtocol = v) }

    // VLESS clients
    fun addVlessClient() = updateForm { copy(vlessClients = vlessClients + VlessClientState()) }
    fun removeVlessClient(index: Int) = updateForm {
        if (vlessClients.size <= 1) this else copy(vlessClients = vlessClients.toMutableList().also { it.removeAt(index) })
    }
    fun updateVlessClient(index: Int, block: VlessClientState.() -> VlessClientState) = updateForm {
        copy(vlessClients = vlessClients.toMutableList().also { it[index] = it[index].block() })
    }

    // VMess
    fun updateVmessDisableInsecure(v: Boolean) = updateForm { copy(vmessDisableInsecure = v) }

    // VMess clients
    fun addVmessClient() = updateForm { copy(vmessClients = vmessClients + VmessClientState()) }
    fun removeVmessClient(index: Int) = updateForm {
        if (vmessClients.size <= 1) this else copy(vmessClients = vmessClients.toMutableList().also { it.removeAt(index) })
    }
    fun updateVmessClient(index: Int, block: VmessClientState.() -> VmessClientState) = updateForm {
        copy(vmessClients = vmessClients.toMutableList().also { it[index] = it[index].block() })
    }

    // Trojan clients
    fun addTrojanClient() = updateForm { copy(trojanClients = trojanClients + TrojanClientState()) }
    fun removeTrojanClient(index: Int) = updateForm {
        if (trojanClients.size <= 1) this else copy(trojanClients = trojanClients.toMutableList().also { it.removeAt(index) })
    }
    fun updateTrojanClient(index: Int, block: TrojanClientState.() -> TrojanClientState) = updateForm {
        copy(trojanClients = trojanClients.toMutableList().also { it[index] = it[index].block() })
    }

    // Shadowsocks
    fun updateSsMethod(v: String) = updateForm { copy(ssMethod = v) }
    fun updateSsPassword(v: String) = updateForm { copy(ssPassword = v) }
    fun updateSsNetwork(v: String) = updateForm { copy(ssNetwork = v) }
    fun addSsClient() = updateForm { copy(ssClients = ssClients + ShadowsocksClientState()) }
    fun removeSsClient(index: Int) = updateForm {
        copy(ssClients = ssClients.toMutableList().also { it.removeAt(index) })
    }
    fun updateSsClient(index: Int, block: ShadowsocksClientState.() -> ShadowsocksClientState) = updateForm {
        copy(ssClients = ssClients.toMutableList().also { it[index] = it[index].block() })
    }

    // Hysteria
    fun updateHysteriaObfsPassword(v: String) = updateForm { copy(hysteriaObfsPassword = v) }
    fun updateHysteriaUdpIdleTimeout(v: String) = updateForm { copy(hysteriaUdpIdleTimeout = v) }
    fun addHysteriaClient() = updateForm { copy(hysteriaClients = hysteriaClients + HysteriaClientState()) }
    fun removeHysteriaClient(index: Int) = updateForm {
        if (hysteriaClients.size <= 1) this else copy(hysteriaClients = hysteriaClients.toMutableList().also { it.removeAt(index) })
    }
    fun updateHysteriaClient(index: Int, block: HysteriaClientState.() -> HysteriaClientState) = updateForm {
        copy(hysteriaClients = hysteriaClients.toMutableList().also { it[index] = it[index].block() })
    }

    // TUN
    fun updateTunMtu(v: String) = updateForm { copy(tunMtu = v) }
    fun updateTunGso(v: Boolean) = updateForm { copy(tunGso = v) }
    fun updateTunGro(v: Boolean) = updateForm { copy(tunGro = v) }
    fun updateTunEnableExFilter(v: Boolean) = updateForm { copy(tunEnableExFilter = v) }
    fun updateTunStrictRoute(v: Boolean) = updateForm { copy(tunStrictRoute = v) }
    fun updateTunRouteAddress(v: String) = updateForm { copy(tunRouteAddress = v) }
    fun updateTunRouteAddressSet(v: String) = updateForm { copy(tunRouteAddressSet = v) }
    fun updateTunRouteExcludeAddress(v: String) = updateForm { copy(tunRouteExcludeAddress = v) }
    fun updateTunRouteExcludeAddressSet(v: String) = updateForm { copy(tunRouteExcludeAddressSet = v) }

    // SOCKS
    fun updateSocksAuth(v: String) = updateForm { copy(socksAuth = v) }
    fun updateSocksUdp(v: Boolean) = updateForm { copy(socksUdp = v) }
    fun updateSocksIp(v: String) = updateForm { copy(socksIp = v) }
    fun addSocksAccount() = updateForm { copy(socksAccounts = socksAccounts + UserPassState()) }
    fun removeSocksAccount(index: Int) = updateForm {
        copy(socksAccounts = socksAccounts.toMutableList().also { it.removeAt(index) })
    }
    fun updateSocksAccount(index: Int, block: UserPassState.() -> UserPassState) = updateForm {
        copy(socksAccounts = socksAccounts.toMutableList().also { it[index] = it[index].block() })
    }

    // HTTP
    fun updateHttpAllowTransparent(v: Boolean) = updateForm { copy(httpAllowTransparent = v) }
    fun addHttpAccount() = updateForm { copy(httpAccounts = httpAccounts + UserPassState()) }
    fun removeHttpAccount(index: Int) = updateForm {
        copy(httpAccounts = httpAccounts.toMutableList().also { it.removeAt(index) })
    }
    fun updateHttpAccount(index: Int, block: UserPassState.() -> UserPassState) = updateForm {
        copy(httpAccounts = httpAccounts.toMutableList().also { it[index] = it[index].block() })
    }

    // WireGuard
    fun updateWgSecretKey(v: String) = updateForm { copy(wgSecretKey = v) }
    fun updateWgMtu(v: String) = updateForm { copy(wgMtu = v) }
    fun addWgPeer() = updateForm { copy(wgPeers = wgPeers + WgPeerState()) }
    fun removeWgPeer(index: Int) = updateForm {
        copy(wgPeers = wgPeers.toMutableList().also { it.removeAt(index) })
    }
    fun updateWgPeer(index: Int, block: WgPeerState.() -> WgPeerState) = updateForm {
        copy(wgPeers = wgPeers.toMutableList().also { it[index] = it[index].block() })
    }

    // Dokodemo
    fun updateDokodemoAddress(v: String) = updateForm { copy(dokodemoAddress = v) }
    fun updateDokodemoTargetPort(v: String) = updateForm { copy(dokodemoTargetPort = v) }
    fun updateDokodemoNetwork(v: String) = updateForm { copy(dokodemoNetwork = v) }
    fun updateDokodemoFollowRedirect(v: Boolean) = updateForm { copy(dokodemoFollowRedirect = v) }

    // Stream
    fun updateSelectedNetwork(v: NetworkType) = updateForm { copy(selectedNetwork = v) }
    fun updateTcpHeaderType(v: String) = updateForm { copy(tcpHeaderType = v) }
    fun updateTcpHttpPath(v: String) = updateForm { copy(tcpHttpPath = v) }
    fun updateTcpHttpHost(v: String) = updateForm { copy(tcpHttpHost = v) }
    fun updateWsPath(v: String) = updateForm { copy(wsPath = v) }
    fun updateWsHost(v: String) = updateForm { copy(wsHost = v) }
    fun updateGrpcServiceName(v: String) = updateForm { copy(grpcServiceName = v) }
    fun updateGrpcAuthority(v: String) = updateForm { copy(grpcAuthority = v) }
    fun updateGrpcMultiMode(v: Boolean) = updateForm { copy(grpcMultiMode = v) }
    fun updateHttpUpgradePath(v: String) = updateForm { copy(httpUpgradePath = v) }
    fun updateHttpUpgradeHost(v: String) = updateForm { copy(httpUpgradeHost = v) }
    fun updateXhttpPath(v: String) = updateForm { copy(xhttpPath = v) }
    fun updateXhttpHost(v: String) = updateForm { copy(xhttpHost = v) }
    fun updateXhttpMode(v: String) = updateForm { copy(xhttpMode = v) }
    fun updateKcpMtu(v: String) = updateForm { copy(kcpMtu = v) }
    fun updateKcpTti(v: String) = updateForm { copy(kcpTti = v) }
    fun updateKcpUplinkCapacity(v: String) = updateForm { copy(kcpUplinkCapacity = v) }
    fun updateKcpDownlinkCapacity(v: String) = updateForm { copy(kcpDownlinkCapacity = v) }
    fun updateKcpCongestion(v: Boolean) = updateForm { copy(kcpCongestion = v) }
    fun updateKcpReadBufferSize(v: String) = updateForm { copy(kcpReadBufferSize = v) }
    fun updateKcpWriteBufferSize(v: String) = updateForm { copy(kcpWriteBufferSize = v) }
    fun updateKcpSeed(v: String) = updateForm { copy(kcpSeed = v) }
    fun updateKcpHeaderType(v: String) = updateForm { copy(kcpHeaderType = v) }

    // Security
    fun updateSelectedSecurity(v: SecurityType) = updateForm { copy(selectedSecurity = v) }
    fun updateTlsServerName(v: String) = updateForm { copy(tlsServerName = v) }
    fun updateTlsMinVersion(v: String) = updateForm { copy(tlsMinVersion = v) }
    fun updateTlsMaxVersion(v: String) = updateForm { copy(tlsMaxVersion = v) }
    fun toggleTlsAlpn(tag: String) = updateForm {
        val newSet = if (tag in tlsAlpn) tlsAlpn - tag else tlsAlpn + tag
        copy(tlsAlpn = newSet)
    }
    fun updateTlsCertificateFile(v: String) = updateForm { copy(tlsCertificateFile = v) }
    fun updateTlsKeyFile(v: String) = updateForm { copy(tlsKeyFile = v) }
    fun updateTlsFingerprint(v: String) = updateForm { copy(tlsFingerprint = v) }
    fun updateRealityDest(v: String) = updateForm { copy(realityDest = v) }
    fun updateRealityServerNames(v: String) = updateForm { copy(realityServerNames = v) }
    fun updateRealityPrivateKey(v: String) = updateForm { copy(realityPrivateKey = v) }
    fun updateRealityPublicKey(v: String) = updateForm { copy(realityPublicKey = v) }
    fun updateRealityShortIds(v: String) = updateForm { copy(realityShortIds = v) }
    fun updateRealityFingerprint(v: String) = updateForm { copy(realityFingerprint = v) }

    // Sniffing
    fun updateSniffingEnabled(v: Boolean) = updateForm { copy(sniffingEnabled = v) }
    fun toggleSniffingDestOverride(tag: String) = updateForm {
        val newSet = if (tag in sniffingDestOverride) sniffingDestOverride - tag else sniffingDestOverride + tag
        copy(sniffingDestOverride = newSet)
    }
    fun updateSniffingMetadataOnly(v: Boolean) = updateForm { copy(sniffingMetadataOnly = v) }
    fun updateSniffingRouteOnly(v: Boolean) = updateForm { copy(sniffingRouteOnly = v) }

    // ---- actions ----

    fun generateUuid(onResult: (String) -> Unit) {
        val panel = activePanel
        if (panel == null) {
            onResult(randomUuid())
            return
        }
        viewModelScope.launch {
            val result = xuiClient.fetchNewUuid(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )
            onResult(
                when (result) {
                    is Result.Success -> result.data
                    is Result.Failure -> randomUuid()
                },
            )
        }
    }

    fun generateX25519() {
        val panel = activePanel ?: return
        viewModelScope.launch {
            when (val result = xuiClient.fetchNewX25519(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            )) {
                is Result.Success -> updateForm {
                    copy(
                        realityPrivateKey = result.data.privateKey,
                        realityPublicKey = result.data.publicKey,
                    )
                }
                is Result.Failure -> { /* silently ignore — user can retry */ }
            }
        }
    }

    fun save() {
        val editing = _uiState.value as? AddInboundUiState.Editing ?: return
        val panel = activePanel ?: run {
            _uiState.value = editing.copy(errorMessage = "Нет активной панели")
            return
        }
        _uiState.value = editing.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val draft = editing.formState.toInboundDraft()
            val dto = InboundEncoder.encode(draft)
            val editId = editing.editingInboundId
            val result = if (editId != null) {
                xuiClient.updateInbound(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                    id = editId,
                    body = dto,
                )
            } else {
                xuiClient.addInbound(
                    panelId = panel.id,
                    baseUrl = panel.baseUrl,
                    auth = panel.toAuth(),
                    tls = panel.toPanelTls(),
                    body = dto,
                )
            }
            when (result) {
                is Result.Success -> _uiState.value = AddInboundUiState.Saved
                is Result.Failure -> {
                    _uiState.value = ((_uiState.value as? AddInboundUiState.Editing) ?: editing).copy(
                        isSaving = false,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun errorShown() {
        val editing = _uiState.value as? AddInboundUiState.Editing ?: return
        _uiState.value = editing.copy(errorMessage = null)
    }

    private fun DomainError.toUserMessage(): String = when (this) {
        is DomainError.InvalidCredentials -> "Неверный логин или пароль"
        is DomainError.Tls -> "Ошибка TLS: $message"
        is DomainError.Network -> {
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
            "Нет соединения с панелью ($detail)"
        }
        is DomainError.PanelUnreachable -> "Панель недоступна"
        is DomainError.PanelResponse -> "Ответ панели: $body"
        is DomainError.Unexpected -> {
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
            "Неожиданная ошибка ($detail)"
        }
        is DomainError.PinMismatch -> "Сертификат панели изменился"
    }
}
