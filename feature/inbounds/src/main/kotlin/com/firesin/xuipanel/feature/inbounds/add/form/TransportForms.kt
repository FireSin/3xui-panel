package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.NetworkType

@Composable
fun TransportForms(
    formState: AddInboundFormState,
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
) {
    when (formState.selectedNetwork) {
        NetworkType.TCP -> TcpTransportForm(
            headerType = formState.tcpHeaderType,
            httpPath = formState.tcpHttpPath,
            httpHost = formState.tcpHttpHost,
            onHeaderTypeChange = onTcpHeaderTypeChange,
            onHttpPathChange = onTcpHttpPathChange,
            onHttpHostChange = onTcpHttpHostChange,
        )
        NetworkType.WS -> WsTransportForm(
            path = formState.wsPath,
            host = formState.wsHost,
            onPathChange = onWsPathChange,
            onHostChange = onWsHostChange,
        )
        NetworkType.GRPC -> GrpcTransportForm(
            serviceName = formState.grpcServiceName,
            authority = formState.grpcAuthority,
            multiMode = formState.grpcMultiMode,
            onServiceNameChange = onGrpcServiceNameChange,
            onAuthorityChange = onGrpcAuthorityChange,
            onMultiModeChange = onGrpcMultiModeChange,
        )
        NetworkType.HTTPUPGRADE -> HttpUpgradeTransportForm(
            path = formState.httpUpgradePath,
            host = formState.httpUpgradeHost,
            onPathChange = onHttpUpgradePathChange,
            onHostChange = onHttpUpgradeHostChange,
        )
        NetworkType.XHTTP -> XhttpTransportForm(
            path = formState.xhttpPath,
            host = formState.xhttpHost,
            mode = formState.xhttpMode,
            onPathChange = onXhttpPathChange,
            onHostChange = onXhttpHostChange,
            onModeChange = onXhttpModeChange,
        )
        NetworkType.KCP -> KcpTransportForm(
            mtu = formState.kcpMtu,
            tti = formState.kcpTti,
            uplinkCapacity = formState.kcpUplinkCapacity,
            downlinkCapacity = formState.kcpDownlinkCapacity,
            congestion = formState.kcpCongestion,
            readBufferSize = formState.kcpReadBufferSize,
            writeBufferSize = formState.kcpWriteBufferSize,
            seed = formState.kcpSeed,
            headerType = formState.kcpHeaderType,
            onMtuChange = onKcpMtuChange,
            onTtiChange = onKcpTtiChange,
            onUplinkCapacityChange = onKcpUplinkCapacityChange,
            onDownlinkCapacityChange = onKcpDownlinkCapacityChange,
            onCongestionChange = onKcpCongestionChange,
            onReadBufferSizeChange = onKcpReadBufferSizeChange,
            onWriteBufferSizeChange = onKcpWriteBufferSizeChange,
            onSeedChange = onKcpSeedChange,
            onHeaderTypeChange = onKcpHeaderTypeChange,
        )
    }
}

@Composable
private fun TcpTransportForm(
    headerType: String,
    httpPath: String,
    httpHost: String,
    onHeaderTypeChange: (String) -> Unit,
    onHttpPathChange: (String) -> Unit,
    onHttpHostChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.add_inbound_section_tcp_settings))
    GroupCard {
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_header_type),
                value = headerType,
                onValueChange = {},
                readOnly = true,
                topDivider = false,
                trailing = {
                    TextButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.add_inbound_change))
                    }
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("none", "http").forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { onHeaderTypeChange(t); expanded = false })
                }
            }
        }
        if (headerType == "http") {
            FieldRow(label = stringResource(R.string.add_inbound_field_path), value = httpPath, onValueChange = onHttpPathChange)
            FieldRow(label = stringResource(R.string.add_inbound_field_host), value = httpHost, onValueChange = onHttpHostChange)
        }
    }
}

@Composable
private fun WsTransportForm(path: String, host: String, onPathChange: (String) -> Unit, onHostChange: (String) -> Unit) {
    SectionHeader(stringResource(R.string.add_inbound_section_ws_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_path), value = path, onValueChange = onPathChange, topDivider = false)
        FieldRow(label = stringResource(R.string.add_inbound_field_host), value = host, onValueChange = onHostChange)
    }
}

@Composable
private fun GrpcTransportForm(
    serviceName: String,
    authority: String,
    multiMode: Boolean,
    onServiceNameChange: (String) -> Unit,
    onAuthorityChange: (String) -> Unit,
    onMultiModeChange: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_grpc_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_service_name), value = serviceName, onValueChange = onServiceNameChange, topDivider = false)
        FieldRow(label = stringResource(R.string.add_inbound_field_authority), value = authority, onValueChange = onAuthorityChange)
        FieldRow(
            label = stringResource(R.string.add_inbound_field_multi_mode),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = multiMode, onCheckedChange = onMultiModeChange) },
        )
    }
}

@Composable
private fun HttpUpgradeTransportForm(path: String, host: String, onPathChange: (String) -> Unit, onHostChange: (String) -> Unit) {
    SectionHeader(stringResource(R.string.add_inbound_section_httpupgrade_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_path), value = path, onValueChange = onPathChange, topDivider = false)
        FieldRow(label = stringResource(R.string.add_inbound_field_host), value = host, onValueChange = onHostChange)
    }
}

@Composable
private fun XhttpTransportForm(
    path: String,
    host: String,
    mode: String,
    onPathChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onModeChange: (String) -> Unit,
) {
    var modeExpanded by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.add_inbound_section_xhttp_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_path), value = path, onValueChange = onPathChange, topDivider = false)
        FieldRow(label = stringResource(R.string.add_inbound_field_host), value = host, onValueChange = onHostChange)
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_mode),
                value = mode,
                onValueChange = {},
                readOnly = true,
                trailing = {
                    TextButton(onClick = { modeExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) }
                },
            )
            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                listOf("auto", "packet-up", "stream-up", "stream-one").forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { onModeChange(m); modeExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun KcpTransportForm(
    mtu: String, tti: String, uplinkCapacity: String, downlinkCapacity: String,
    congestion: Boolean, readBufferSize: String, writeBufferSize: String, seed: String, headerType: String,
    onMtuChange: (String) -> Unit, onTtiChange: (String) -> Unit,
    onUplinkCapacityChange: (String) -> Unit, onDownlinkCapacityChange: (String) -> Unit,
    onCongestionChange: (Boolean) -> Unit, onReadBufferSizeChange: (String) -> Unit,
    onWriteBufferSizeChange: (String) -> Unit, onSeedChange: (String) -> Unit, onHeaderTypeChange: (String) -> Unit,
) {
    var headerExpanded by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.add_inbound_section_kcp_settings))
    GroupCard {
        FieldRow(label = "MTU", value = mtu, onValueChange = onMtuChange, topDivider = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(label = "TTI", value = tti, onValueChange = onTtiChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(label = stringResource(R.string.add_inbound_field_uplink_capacity), value = uplinkCapacity, onValueChange = onUplinkCapacityChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(label = stringResource(R.string.add_inbound_field_downlink_capacity), value = downlinkCapacity, onValueChange = onDownlinkCapacityChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(
            label = stringResource(R.string.add_inbound_field_congestion),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = congestion, onCheckedChange = onCongestionChange) },
        )
        FieldRow(label = stringResource(R.string.add_inbound_field_read_buffer), value = readBufferSize, onValueChange = onReadBufferSizeChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(label = stringResource(R.string.add_inbound_field_write_buffer), value = writeBufferSize, onValueChange = onWriteBufferSizeChange, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FieldRow(label = stringResource(R.string.add_inbound_field_seed), value = seed, onValueChange = onSeedChange)
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_header_type),
                value = headerType,
                onValueChange = {},
                readOnly = true,
                trailing = { TextButton(onClick = { headerExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) } },
            )
            DropdownMenu(expanded = headerExpanded, onDismissRequest = { headerExpanded = false }) {
                listOf("none", "srtp", "utp", "wechat-video", "dtls", "wireguard").forEach { h ->
                    DropdownMenuItem(text = { Text(h) }, onClick = { onHeaderTypeChange(h); headerExpanded = false })
                }
            }
        }
    }
}
