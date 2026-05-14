package com.firesin.xuipanel.feature.inbounds.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.form.CommonSection
import com.firesin.xuipanel.feature.inbounds.add.form.DokodemoForm
import com.firesin.xuipanel.feature.inbounds.add.form.HttpForm
import com.firesin.xuipanel.feature.inbounds.add.form.HysteriaForm
import com.firesin.xuipanel.feature.inbounds.add.form.ProtocolPickerSection
import com.firesin.xuipanel.feature.inbounds.add.form.TunForm
import com.firesin.xuipanel.feature.inbounds.add.form.SecurityForms
import com.firesin.xuipanel.feature.inbounds.add.form.ShadowsocksForm
import com.firesin.xuipanel.feature.inbounds.add.form.SniffingSection
import com.firesin.xuipanel.feature.inbounds.add.form.SocksForm
import com.firesin.xuipanel.feature.inbounds.add.form.StreamSection
import com.firesin.xuipanel.feature.inbounds.add.form.TrojanForm
import com.firesin.xuipanel.feature.inbounds.add.form.VlessForm
import com.firesin.xuipanel.feature.inbounds.add.form.VmessForm
import com.firesin.xuipanel.feature.inbounds.add.form.WireguardForm
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundUiState
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundViewModel
import com.firesin.xuipanel.feature.inbounds.add.ui.ProtocolType

private val STREAM_PROTOCOLS = setOf(
    ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN, ProtocolType.SHADOWSOCKS, ProtocolType.HYSTERIA
)

@Composable
fun AddInboundScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddInboundViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableNodes by viewModel.availableNodes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is AddInboundUiState.Saved) onSaved()
    }

    val editing = uiState as? AddInboundUiState.Editing

    LaunchedEffect(editing?.errorMessage) {
        editing?.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.errorShown()
        }
    }

    AddInboundContent(
        formState = editing?.formState ?: AddInboundFormState(),
        availableNodes = availableNodes,
        isSaving = editing?.isSaving ?: false,
        isLoading = editing?.isLoading ?: false,
        isEditing = editing?.editingInboundId != null,
        snackbarHostState = snackbarHostState,
        onClose = onClose,
        onSave = viewModel::save,
        onRemarkChange = viewModel::updateRemark,
        onNodeIdChange = viewModel::updateNodeId,
        onPortChange = viewModel::updatePort,
        onListenChange = viewModel::updateListen,
        onEnableChange = viewModel::updateEnable,
        onExpiryTimeChange = viewModel::updateExpiryTime,
        onTotalGbChange = viewModel::updateTotalGb,
        onProtocolChange = viewModel::updateSelectedProtocol,
        onAddVlessClient = viewModel::addVlessClient,
        onRemoveVlessClient = viewModel::removeVlessClient,
        onUpdateVlessClient = viewModel::updateVlessClient,
        onGenerateVlessUuid = { index ->
            viewModel.generateUuid { uuid ->
                viewModel.updateVlessClient(index) { copy(id = uuid) }
            }
        },
        onAddVmessClient = viewModel::addVmessClient,
        onRemoveVmessClient = viewModel::removeVmessClient,
        onUpdateVmessClient = viewModel::updateVmessClient,
        onGenerateVmessUuid = { index ->
            viewModel.generateUuid { uuid ->
                viewModel.updateVmessClient(index) { copy(id = uuid) }
            }
        },
        onVmessDisableInsecureChange = viewModel::updateVmessDisableInsecure,
        onAddTrojanClient = viewModel::addTrojanClient,
        onRemoveTrojanClient = viewModel::removeTrojanClient,
        onUpdateTrojanClient = viewModel::updateTrojanClient,
        onSsMethodChange = viewModel::updateSsMethod,
        onSsPasswordChange = viewModel::updateSsPassword,
        onSsNetworkChange = viewModel::updateSsNetwork,
        onAddSsClient = viewModel::addSsClient,
        onRemoveSsClient = viewModel::removeSsClient,
        onUpdateSsClient = viewModel::updateSsClient,
        onHysteriaObfsPasswordChange = viewModel::updateHysteriaObfsPassword,
        onHysteriaUdpIdleTimeoutChange = viewModel::updateHysteriaUdpIdleTimeout,
        onAddHysteriaClient = viewModel::addHysteriaClient,
        onRemoveHysteriaClient = viewModel::removeHysteriaClient,
        onUpdateHysteriaClient = viewModel::updateHysteriaClient,
        onTunMtuChange = viewModel::updateTunMtu,
        onTunGsoChange = viewModel::updateTunGso,
        onTunGroChange = viewModel::updateTunGro,
        onTunEnableExFilterChange = viewModel::updateTunEnableExFilter,
        onTunStrictRouteChange = viewModel::updateTunStrictRoute,
        onTunRouteAddressChange = viewModel::updateTunRouteAddress,
        onTunRouteAddressSetChange = viewModel::updateTunRouteAddressSet,
        onTunRouteExcludeAddressChange = viewModel::updateTunRouteExcludeAddress,
        onTunRouteExcludeAddressSetChange = viewModel::updateTunRouteExcludeAddressSet,
        onSocksAuthChange = viewModel::updateSocksAuth,
        onSocksUdpChange = viewModel::updateSocksUdp,
        onSocksIpChange = viewModel::updateSocksIp,
        onAddSocksAccount = viewModel::addSocksAccount,
        onRemoveSocksAccount = viewModel::removeSocksAccount,
        onUpdateSocksAccount = viewModel::updateSocksAccount,
        onHttpAllowTransparentChange = viewModel::updateHttpAllowTransparent,
        onAddHttpAccount = viewModel::addHttpAccount,
        onRemoveHttpAccount = viewModel::removeHttpAccount,
        onUpdateHttpAccount = viewModel::updateHttpAccount,
        onWgSecretKeyChange = viewModel::updateWgSecretKey,
        onWgMtuChange = viewModel::updateWgMtu,
        onAddWgPeer = viewModel::addWgPeer,
        onRemoveWgPeer = viewModel::removeWgPeer,
        onUpdateWgPeer = viewModel::updateWgPeer,
        onDokodemoAddressChange = viewModel::updateDokodemoAddress,
        onDokodemoTargetPortChange = viewModel::updateDokodemoTargetPort,
        onDokodemoNetworkChange = viewModel::updateDokodemoNetwork,
        onDokodemoFollowRedirectChange = viewModel::updateDokodemoFollowRedirect,
        onNetworkChange = viewModel::updateSelectedNetwork,
        onSecurityChange = viewModel::updateSelectedSecurity,
        onTcpHeaderTypeChange = viewModel::updateTcpHeaderType,
        onTcpHttpPathChange = viewModel::updateTcpHttpPath,
        onTcpHttpHostChange = viewModel::updateTcpHttpHost,
        onWsPathChange = viewModel::updateWsPath,
        onWsHostChange = viewModel::updateWsHost,
        onGrpcServiceNameChange = viewModel::updateGrpcServiceName,
        onGrpcAuthorityChange = viewModel::updateGrpcAuthority,
        onGrpcMultiModeChange = viewModel::updateGrpcMultiMode,
        onHttpUpgradePathChange = viewModel::updateHttpUpgradePath,
        onHttpUpgradeHostChange = viewModel::updateHttpUpgradeHost,
        onXhttpPathChange = viewModel::updateXhttpPath,
        onXhttpHostChange = viewModel::updateXhttpHost,
        onXhttpModeChange = viewModel::updateXhttpMode,
        onKcpMtuChange = viewModel::updateKcpMtu,
        onKcpTtiChange = viewModel::updateKcpTti,
        onKcpUplinkCapacityChange = viewModel::updateKcpUplinkCapacity,
        onKcpDownlinkCapacityChange = viewModel::updateKcpDownlinkCapacity,
        onKcpCongestionChange = viewModel::updateKcpCongestion,
        onKcpReadBufferSizeChange = viewModel::updateKcpReadBufferSize,
        onKcpWriteBufferSizeChange = viewModel::updateKcpWriteBufferSize,
        onKcpSeedChange = viewModel::updateKcpSeed,
        onKcpHeaderTypeChange = viewModel::updateKcpHeaderType,
        onTlsServerNameChange = viewModel::updateTlsServerName,
        onTlsMinVersionChange = viewModel::updateTlsMinVersion,
        onTlsMaxVersionChange = viewModel::updateTlsMaxVersion,
        onToggleTlsAlpn = viewModel::toggleTlsAlpn,
        onTlsCertificateFileChange = viewModel::updateTlsCertificateFile,
        onTlsKeyFileChange = viewModel::updateTlsKeyFile,
        onTlsFingerprintChange = viewModel::updateTlsFingerprint,
        onRealityDestChange = viewModel::updateRealityDest,
        onRealityServerNamesChange = viewModel::updateRealityServerNames,
        onRealityPrivateKeyChange = viewModel::updateRealityPrivateKey,
        onRealityPublicKeyChange = viewModel::updateRealityPublicKey,
        onRealityShortIdsChange = viewModel::updateRealityShortIds,
        onRealityFingerprintChange = viewModel::updateRealityFingerprint,
        onGenerateX25519 = viewModel::generateX25519,
        onSniffingEnabledChange = viewModel::updateSniffingEnabled,
        onToggleSniffingDestOverride = viewModel::toggleSniffingDestOverride,
        onSniffingMetadataOnlyChange = viewModel::updateSniffingMetadataOnly,
        onSniffingRouteOnlyChange = viewModel::updateSniffingRouteOnly,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun AddInboundContent(
    formState: AddInboundFormState,
    availableNodes: List<NodeDto> = emptyList(),
    isSaving: Boolean,
    isLoading: Boolean = false,
    isEditing: Boolean = false,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRemarkChange: (String) -> Unit,
    onNodeIdChange: (Int?) -> Unit = {},
    onPortChange: (String) -> Unit,
    onListenChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onExpiryTimeChange: (Long) -> Unit,
    onTotalGbChange: (String) -> Unit,
    onProtocolChange: (ProtocolType) -> Unit,
    onAddVlessClient: () -> Unit,
    onRemoveVlessClient: (Int) -> Unit,
    onUpdateVlessClient: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.VlessClientState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.VlessClientState) -> Unit,
    onGenerateVlessUuid: (Int) -> Unit,
    onAddVmessClient: () -> Unit,
    onRemoveVmessClient: (Int) -> Unit,
    onUpdateVmessClient: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.VmessClientState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.VmessClientState) -> Unit,
    onGenerateVmessUuid: (Int) -> Unit,
    onVmessDisableInsecureChange: (Boolean) -> Unit,
    onAddTrojanClient: () -> Unit,
    onRemoveTrojanClient: (Int) -> Unit,
    onUpdateTrojanClient: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.TrojanClientState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.TrojanClientState) -> Unit,
    onSsMethodChange: (String) -> Unit,
    onSsPasswordChange: (String) -> Unit,
    onSsNetworkChange: (String) -> Unit,
    onAddSsClient: () -> Unit,
    onRemoveSsClient: (Int) -> Unit,
    onUpdateSsClient: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.ShadowsocksClientState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.ShadowsocksClientState) -> Unit,
    onHysteriaObfsPasswordChange: (String) -> Unit,
    onHysteriaUdpIdleTimeoutChange: (String) -> Unit,
    onAddHysteriaClient: () -> Unit,
    onRemoveHysteriaClient: (Int) -> Unit,
    onUpdateHysteriaClient: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.HysteriaClientState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.HysteriaClientState) -> Unit,
    onTunMtuChange: (String) -> Unit,
    onTunGsoChange: (Boolean) -> Unit,
    onTunGroChange: (Boolean) -> Unit,
    onTunEnableExFilterChange: (Boolean) -> Unit,
    onTunStrictRouteChange: (Boolean) -> Unit,
    onTunRouteAddressChange: (String) -> Unit,
    onTunRouteAddressSetChange: (String) -> Unit,
    onTunRouteExcludeAddressChange: (String) -> Unit,
    onTunRouteExcludeAddressSetChange: (String) -> Unit,
    onSocksAuthChange: (String) -> Unit,
    onSocksUdpChange: (Boolean) -> Unit,
    onSocksIpChange: (String) -> Unit,
    onAddSocksAccount: () -> Unit,
    onRemoveSocksAccount: (Int) -> Unit,
    onUpdateSocksAccount: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState) -> Unit,
    onHttpAllowTransparentChange: (Boolean) -> Unit,
    onAddHttpAccount: () -> Unit,
    onRemoveHttpAccount: (Int) -> Unit,
    onUpdateHttpAccount: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState) -> Unit,
    onWgSecretKeyChange: (String) -> Unit,
    onWgMtuChange: (String) -> Unit,
    onAddWgPeer: () -> Unit,
    onRemoveWgPeer: (Int) -> Unit,
    onUpdateWgPeer: (Int, com.firesin.xuipanel.feature.inbounds.add.ui.WgPeerState.() -> com.firesin.xuipanel.feature.inbounds.add.ui.WgPeerState) -> Unit,
    onDokodemoAddressChange: (String) -> Unit,
    onDokodemoTargetPortChange: (String) -> Unit,
    onDokodemoNetworkChange: (String) -> Unit,
    onDokodemoFollowRedirectChange: (Boolean) -> Unit,
    onNetworkChange: (com.firesin.xuipanel.feature.inbounds.add.ui.NetworkType) -> Unit,
    onSecurityChange: (com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType) -> Unit,
    onTcpHeaderTypeChange: (String) -> Unit,
    onTcpHttpPathChange: (String) -> Unit,
    onTcpHttpHostChange: (String) -> Unit,
    onWsPathChange: (String) -> Unit,
    onWsHostChange: (String) -> Unit,
    onGrpcServiceNameChange: (String) -> Unit,
    onGrpcAuthorityChange: (String) -> Unit,
    onGrpcMultiModeChange: (Boolean) -> Unit,
    onHttpUpgradePathChange: (String) -> Unit,
    onHttpUpgradeHostChange: (String) -> Unit,
    onXhttpPathChange: (String) -> Unit,
    onXhttpHostChange: (String) -> Unit,
    onXhttpModeChange: (String) -> Unit,
    onKcpMtuChange: (String) -> Unit,
    onKcpTtiChange: (String) -> Unit,
    onKcpUplinkCapacityChange: (String) -> Unit,
    onKcpDownlinkCapacityChange: (String) -> Unit,
    onKcpCongestionChange: (Boolean) -> Unit,
    onKcpReadBufferSizeChange: (String) -> Unit,
    onKcpWriteBufferSizeChange: (String) -> Unit,
    onKcpSeedChange: (String) -> Unit,
    onKcpHeaderTypeChange: (String) -> Unit,
    onTlsServerNameChange: (String) -> Unit,
    onTlsMinVersionChange: (String) -> Unit,
    onTlsMaxVersionChange: (String) -> Unit,
    onToggleTlsAlpn: (String) -> Unit,
    onTlsCertificateFileChange: (String) -> Unit,
    onTlsKeyFileChange: (String) -> Unit,
    onTlsFingerprintChange: (String) -> Unit,
    onRealityDestChange: (String) -> Unit,
    onRealityServerNamesChange: (String) -> Unit,
    onRealityPrivateKeyChange: (String) -> Unit,
    onRealityPublicKeyChange: (String) -> Unit,
    onRealityShortIdsChange: (String) -> Unit,
    onRealityFingerprintChange: (String) -> Unit,
    onGenerateX25519: () -> Unit,
    onSniffingEnabledChange: (Boolean) -> Unit,
    onToggleSniffingDestOverride: (String) -> Unit,
    onSniffingMetadataOnlyChange: (Boolean) -> Unit,
    onSniffingRouteOnlyChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.edit_inbound_title else R.string.add_inbound_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_inbound_cd_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!isSaving && !isLoading) onSave() },
                icon = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                text = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.add_inbound_button_save else R.string.add_inbound_save
                        )
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CommonSection(
                remark = formState.remark,
                port = formState.port,
                listen = formState.listen,
                enable = formState.enable,
                expiryTime = formState.expiryTime,
                totalGb = formState.totalGb,
                onRemarkChange = onRemarkChange,
                onPortChange = onPortChange,
                onListenChange = onListenChange,
                onEnableChange = onEnableChange,
                onExpiryTimeChange = onExpiryTimeChange,
                onTotalGbChange = onTotalGbChange,
                nodeId = formState.nodeId,
                availableNodes = availableNodes,
                onNodeIdChange = onNodeIdChange,
                isEditing = isEditing,
            )

            Spacer(Modifier.height(12.dp))

            ProtocolPickerSection(
                selected = formState.selectedProtocol,
                onSelected = onProtocolChange,
                enabled = !isEditing,
            )

            Spacer(Modifier.height(12.dp))

            // Protocol-specific form
            when (formState.selectedProtocol) {
                ProtocolType.VLESS -> VlessForm(
                    clients = formState.vlessClients,
                    onAddClient = onAddVlessClient,
                    onRemoveClient = onRemoveVlessClient,
                    onUpdateClient = onUpdateVlessClient,
                    onGenerateUuid = onGenerateVlessUuid,
                )
                ProtocolType.VMESS -> VmessForm(
                    clients = formState.vmessClients,
                    disableInsecureEncryption = formState.vmessDisableInsecure,
                    onAddClient = onAddVmessClient,
                    onRemoveClient = onRemoveVmessClient,
                    onUpdateClient = onUpdateVmessClient,
                    onGenerateUuid = onGenerateVmessUuid,
                    onDisableInsecureChange = onVmessDisableInsecureChange,
                )
                ProtocolType.TROJAN -> TrojanForm(
                    clients = formState.trojanClients,
                    onAddClient = onAddTrojanClient,
                    onRemoveClient = onRemoveTrojanClient,
                    onUpdateClient = onUpdateTrojanClient,
                )
                ProtocolType.SHADOWSOCKS -> ShadowsocksForm(
                    method = formState.ssMethod,
                    password = formState.ssPassword,
                    network = formState.ssNetwork,
                    clients = formState.ssClients,
                    onMethodChange = onSsMethodChange,
                    onPasswordChange = onSsPasswordChange,
                    onNetworkChange = onSsNetworkChange,
                    onAddClient = onAddSsClient,
                    onRemoveClient = onRemoveSsClient,
                    onUpdateClient = onUpdateSsClient,
                )
                ProtocolType.SOCKS -> SocksForm(
                    auth = formState.socksAuth,
                    accounts = formState.socksAccounts,
                    udp = formState.socksUdp,
                    ip = formState.socksIp,
                    onAuthChange = onSocksAuthChange,
                    onAddAccount = onAddSocksAccount,
                    onRemoveAccount = onRemoveSocksAccount,
                    onUpdateAccount = onUpdateSocksAccount,
                    onUdpChange = onSocksUdpChange,
                    onIpChange = onSocksIpChange,
                )
                ProtocolType.HTTP -> HttpForm(
                    accounts = formState.httpAccounts,
                    allowTransparent = formState.httpAllowTransparent,
                    onAllowTransparentChange = onHttpAllowTransparentChange,
                    onAddAccount = onAddHttpAccount,
                    onRemoveAccount = onRemoveHttpAccount,
                    onUpdateAccount = onUpdateHttpAccount,
                )
                ProtocolType.WIREGUARD -> WireguardForm(
                    secretKey = formState.wgSecretKey,
                    mtu = formState.wgMtu,
                    peers = formState.wgPeers,
                    onSecretKeyChange = onWgSecretKeyChange,
                    onMtuChange = onWgMtuChange,
                    onAddPeer = onAddWgPeer,
                    onRemovePeer = onRemoveWgPeer,
                    onUpdatePeer = onUpdateWgPeer,
                )
                ProtocolType.DOKODEMO -> DokodemoForm(
                    address = formState.dokodemoAddress,
                    targetPort = formState.dokodemoTargetPort,
                    network = formState.dokodemoNetwork,
                    followRedirect = formState.dokodemoFollowRedirect,
                    onAddressChange = onDokodemoAddressChange,
                    onTargetPortChange = onDokodemoTargetPortChange,
                    onNetworkChange = onDokodemoNetworkChange,
                    onFollowRedirectChange = onDokodemoFollowRedirectChange,
                )
                ProtocolType.HYSTERIA -> HysteriaForm(
                    obfsPassword = formState.hysteriaObfsPassword,
                    udpIdleTimeout = formState.hysteriaUdpIdleTimeout,
                    tlsServerName = formState.tlsServerName,
                    tlsCertificateFile = formState.tlsCertificateFile,
                    tlsKeyFile = formState.tlsKeyFile,
                    clients = formState.hysteriaClients,
                    onObfsPasswordChange = onHysteriaObfsPasswordChange,
                    onUdpIdleTimeoutChange = onHysteriaUdpIdleTimeoutChange,
                    onTlsServerNameChange = onTlsServerNameChange,
                    onTlsCertificateFileChange = onTlsCertificateFileChange,
                    onTlsKeyFileChange = onTlsKeyFileChange,
                    onAddClient = onAddHysteriaClient,
                    onRemoveClient = onRemoveHysteriaClient,
                    onUpdateClient = onUpdateHysteriaClient,
                )
                ProtocolType.TUN -> TunForm(
                    mtu = formState.tunMtu,
                    gso = formState.tunGso,
                    gro = formState.tunGro,
                    enableExFilter = formState.tunEnableExFilter,
                    strictRoute = formState.tunStrictRoute,
                    routeAddress = formState.tunRouteAddress,
                    routeAddressSet = formState.tunRouteAddressSet,
                    routeExcludeAddress = formState.tunRouteExcludeAddress,
                    routeExcludeAddressSet = formState.tunRouteExcludeAddressSet,
                    onMtuChange = onTunMtuChange,
                    onGsoChange = onTunGsoChange,
                    onGroChange = onTunGroChange,
                    onEnableExFilterChange = onTunEnableExFilterChange,
                    onStrictRouteChange = onTunStrictRouteChange,
                    onRouteAddressChange = onTunRouteAddressChange,
                    onRouteAddressSetChange = onTunRouteAddressSetChange,
                    onRouteExcludeAddressChange = onTunRouteExcludeAddressChange,
                    onRouteExcludeAddressSetChange = onTunRouteExcludeAddressSetChange,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Stream section only for relevant protocols (HYSTERIA has transport wired into HysteriaForm)
            if (formState.selectedProtocol in STREAM_PROTOCOLS && formState.selectedProtocol != ProtocolType.HYSTERIA) {
                StreamSection(
                    formState = formState,
                    onNetworkChange = onNetworkChange,
                    onSecurityChange = onSecurityChange,
                    onTcpHeaderTypeChange = onTcpHeaderTypeChange,
                    onTcpHttpPathChange = onTcpHttpPathChange,
                    onTcpHttpHostChange = onTcpHttpHostChange,
                    onWsPathChange = onWsPathChange,
                    onWsHostChange = onWsHostChange,
                    onGrpcServiceNameChange = onGrpcServiceNameChange,
                    onGrpcAuthorityChange = onGrpcAuthorityChange,
                    onGrpcMultiModeChange = onGrpcMultiModeChange,
                    onHttpUpgradePathChange = onHttpUpgradePathChange,
                    onHttpUpgradeHostChange = onHttpUpgradeHostChange,
                    onXhttpPathChange = onXhttpPathChange,
                    onXhttpHostChange = onXhttpHostChange,
                    onXhttpModeChange = onXhttpModeChange,
                    onKcpMtuChange = onKcpMtuChange,
                    onKcpTtiChange = onKcpTtiChange,
                    onKcpUplinkCapacityChange = onKcpUplinkCapacityChange,
                    onKcpDownlinkCapacityChange = onKcpDownlinkCapacityChange,
                    onKcpCongestionChange = onKcpCongestionChange,
                    onKcpReadBufferSizeChange = onKcpReadBufferSizeChange,
                    onKcpWriteBufferSizeChange = onKcpWriteBufferSizeChange,
                    onKcpSeedChange = onKcpSeedChange,
                    onKcpHeaderTypeChange = onKcpHeaderTypeChange,
                    onTlsServerNameChange = onTlsServerNameChange,
                    onTlsMinVersionChange = onTlsMinVersionChange,
                    onTlsMaxVersionChange = onTlsMaxVersionChange,
                    onToggleTlsAlpn = onToggleTlsAlpn,
                    onTlsCertificateFileChange = onTlsCertificateFileChange,
                    onTlsKeyFileChange = onTlsKeyFileChange,
                    onTlsFingerprintChange = onTlsFingerprintChange,
                    onRealityDestChange = onRealityDestChange,
                    onRealityServerNamesChange = onRealityServerNamesChange,
                    onRealityPrivateKeyChange = onRealityPrivateKeyChange,
                    onRealityPublicKeyChange = onRealityPublicKeyChange,
                    onRealityShortIdsChange = onRealityShortIdsChange,
                    onRealityFingerprintChange = onRealityFingerprintChange,
                    onGenerateX25519 = onGenerateX25519,
                )
                Spacer(Modifier.height(12.dp))
            }

            SniffingSection(
                enabled = formState.sniffingEnabled,
                destOverride = formState.sniffingDestOverride,
                metadataOnly = formState.sniffingMetadataOnly,
                routeOnly = formState.sniffingRouteOnly,
                onEnabledChange = onSniffingEnabledChange,
                onToggleDestOverride = onToggleSniffingDestOverride,
                onMetadataOnlyChange = onSniffingMetadataOnlyChange,
                onRouteOnlyChange = onSniffingRouteOnlyChange,
            )

            Spacer(Modifier.height(80.dp)) // space for FAB
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddInboundScreenPreview() {
    XuiPanelTheme {
        AddInboundContent(
            formState = AddInboundFormState(),
            isSaving = false,
            snackbarHostState = remember { SnackbarHostState() },
            onClose = {},
            onSave = {},
            onRemarkChange = {},
            onPortChange = {},
            onListenChange = {},
            onEnableChange = {},
            onExpiryTimeChange = {},
            onTotalGbChange = {},
            onProtocolChange = {},
            onAddVlessClient = {},
            onRemoveVlessClient = {},
            onUpdateVlessClient = { _, _ -> },
            onGenerateVlessUuid = {},
            onAddVmessClient = {},
            onRemoveVmessClient = {},
            onUpdateVmessClient = { _, _ -> },
            onGenerateVmessUuid = {},
            onVmessDisableInsecureChange = {},
            onAddTrojanClient = {},
            onRemoveTrojanClient = {},
            onUpdateTrojanClient = { _, _ -> },
            onSsMethodChange = {},
            onSsPasswordChange = {},
            onSsNetworkChange = {},
            onAddSsClient = {},
            onRemoveSsClient = {},
            onUpdateSsClient = { _, _ -> },
            onHysteriaObfsPasswordChange = {},
            onHysteriaUdpIdleTimeoutChange = {},
            onAddHysteriaClient = {},
            onRemoveHysteriaClient = {},
            onUpdateHysteriaClient = { _, _ -> },
            onTunMtuChange = {},
            onTunGsoChange = {},
            onTunGroChange = {},
            onTunEnableExFilterChange = {},
            onTunStrictRouteChange = {},
            onTunRouteAddressChange = {},
            onTunRouteAddressSetChange = {},
            onTunRouteExcludeAddressChange = {},
            onTunRouteExcludeAddressSetChange = {},
            onSocksAuthChange = {},
            onSocksUdpChange = {},
            onSocksIpChange = {},
            onAddSocksAccount = {},
            onRemoveSocksAccount = {},
            onUpdateSocksAccount = { _, _ -> },
            onHttpAllowTransparentChange = {},
            onAddHttpAccount = {},
            onRemoveHttpAccount = {},
            onUpdateHttpAccount = { _, _ -> },
            onWgSecretKeyChange = {},
            onWgMtuChange = {},
            onAddWgPeer = {},
            onRemoveWgPeer = {},
            onUpdateWgPeer = { _, _ -> },
            onDokodemoAddressChange = {},
            onDokodemoTargetPortChange = {},
            onDokodemoNetworkChange = {},
            onDokodemoFollowRedirectChange = {},
            onNetworkChange = {},
            onSecurityChange = {},
            onTcpHeaderTypeChange = {},
            onTcpHttpPathChange = {},
            onTcpHttpHostChange = {},
            onWsPathChange = {},
            onWsHostChange = {},
            onGrpcServiceNameChange = {},
            onGrpcAuthorityChange = {},
            onGrpcMultiModeChange = {},
            onHttpUpgradePathChange = {},
            onHttpUpgradeHostChange = {},
            onXhttpPathChange = {},
            onXhttpHostChange = {},
            onXhttpModeChange = {},
            onKcpMtuChange = {},
            onKcpTtiChange = {},
            onKcpUplinkCapacityChange = {},
            onKcpDownlinkCapacityChange = {},
            onKcpCongestionChange = {},
            onKcpReadBufferSizeChange = {},
            onKcpWriteBufferSizeChange = {},
            onKcpSeedChange = {},
            onKcpHeaderTypeChange = {},
            onTlsServerNameChange = {},
            onTlsMinVersionChange = {},
            onTlsMaxVersionChange = {},
            onToggleTlsAlpn = {},
            onTlsCertificateFileChange = {},
            onTlsKeyFileChange = {},
            onTlsFingerprintChange = {},
            onRealityDestChange = {},
            onRealityServerNamesChange = {},
            onRealityPrivateKeyChange = {},
            onRealityPublicKeyChange = {},
            onRealityShortIdsChange = {},
            onRealityFingerprintChange = {},
            onGenerateX25519 = {},
            onSniffingEnabledChange = {},
            onToggleSniffingDestOverride = {},
            onSniffingMetadataOnlyChange = {},
            onSniffingRouteOnlyChange = {},
        )
    }
}
