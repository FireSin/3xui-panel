package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.NetworkType
import com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType

@Composable
fun StreamSection(
    formState: AddInboundFormState,
    onNetworkChange: (NetworkType) -> Unit,
    onSecurityChange: (SecurityType) -> Unit,
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
) {
    var networkExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_stream))
    GroupCard {
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_network),
                value = formState.selectedNetwork.label,
                onValueChange = {},
                readOnly = true,
                topDivider = false,
                trailing = {
                    TextButton(onClick = { networkExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change))
                    }
                },
            )
            DropdownMenu(expanded = networkExpanded, onDismissRequest = { networkExpanded = false }) {
                NetworkType.entries.forEach { n ->
                    DropdownMenuItem(
                        text = { Text(n.label) },
                        onClick = { onNetworkChange(n); networkExpanded = false },
                    )
                }
            }
        }
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_security),
                value = formState.selectedSecurity.label,
                onValueChange = {},
                readOnly = true,
                trailing = {
                    TextButton(onClick = { securityExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change))
                    }
                },
            )
            DropdownMenu(expanded = securityExpanded, onDismissRequest = { securityExpanded = false }) {
                SecurityType.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { onSecurityChange(s); securityExpanded = false },
                    )
                }
            }
        }
    }

    // Transport sub-form
    TransportForms(
        formState = formState,
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
    )

    // Security sub-form
    SecurityForms(
        formState = formState,
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
}
