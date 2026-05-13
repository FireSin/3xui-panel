package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.AddInboundFormState
import com.firesin.xuipanel.feature.inbounds.add.ui.SecurityType

private val REALITY_FINGERPRINTS = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random")
private val TLS_VERSIONS = listOf("1.0", "1.1", "1.2", "1.3")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityForms(
    formState: AddInboundFormState,
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
    when (formState.selectedSecurity) {
        SecurityType.NONE -> { /* no extra UI */ }
        SecurityType.TLS -> TlsForm(
            serverName = formState.tlsServerName,
            minVersion = formState.tlsMinVersion,
            maxVersion = formState.tlsMaxVersion,
            alpn = formState.tlsAlpn,
            certificateFile = formState.tlsCertificateFile,
            keyFile = formState.tlsKeyFile,
            fingerprint = formState.tlsFingerprint,
            onServerNameChange = onTlsServerNameChange,
            onMinVersionChange = onTlsMinVersionChange,
            onMaxVersionChange = onTlsMaxVersionChange,
            onToggleAlpn = onToggleTlsAlpn,
            onCertificateFileChange = onTlsCertificateFileChange,
            onKeyFileChange = onTlsKeyFileChange,
            onFingerprintChange = onTlsFingerprintChange,
        )
        SecurityType.REALITY -> RealityForm(
            dest = formState.realityDest,
            serverNames = formState.realityServerNames,
            privateKey = formState.realityPrivateKey,
            publicKey = formState.realityPublicKey,
            shortIds = formState.realityShortIds,
            fingerprint = formState.realityFingerprint,
            onDestChange = onRealityDestChange,
            onServerNamesChange = onRealityServerNamesChange,
            onPrivateKeyChange = onRealityPrivateKeyChange,
            onPublicKeyChange = onRealityPublicKeyChange,
            onShortIdsChange = onRealityShortIdsChange,
            onFingerprintChange = onRealityFingerprintChange,
            onGenerateX25519 = onGenerateX25519,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TlsForm(
    serverName: String,
    minVersion: String,
    maxVersion: String,
    alpn: Set<String>,
    certificateFile: String,
    keyFile: String,
    fingerprint: String,
    onServerNameChange: (String) -> Unit,
    onMinVersionChange: (String) -> Unit,
    onMaxVersionChange: (String) -> Unit,
    onToggleAlpn: (String) -> Unit,
    onCertificateFileChange: (String) -> Unit,
    onKeyFileChange: (String) -> Unit,
    onFingerprintChange: (String) -> Unit,
) {
    var minExpanded by remember { mutableStateOf(false) }
    var maxExpanded by remember { mutableStateOf(false) }
    var fpExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_tls_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_server_name), value = serverName, onValueChange = onServerNameChange, topDivider = false)
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_min_version),
                value = minVersion,
                onValueChange = {},
                readOnly = true,
                trailing = { TextButton(onClick = { minExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) } },
            )
            DropdownMenu(expanded = minExpanded, onDismissRequest = { minExpanded = false }) {
                TLS_VERSIONS.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { onMinVersionChange(v); minExpanded = false }) }
            }
        }
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_max_version),
                value = maxVersion,
                onValueChange = {},
                readOnly = true,
                trailing = { TextButton(onClick = { maxExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) } },
            )
            DropdownMenu(expanded = maxExpanded, onDismissRequest = { maxExpanded = false }) {
                TLS_VERSIONS.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { onMaxVersionChange(v); maxExpanded = false }) }
            }
        }
        FieldRow(label = stringResource(R.string.add_inbound_field_cert_file), value = certificateFile, onValueChange = onCertificateFileChange, stacked = true)
        FieldRow(label = stringResource(R.string.add_inbound_field_key_file), value = keyFile, onValueChange = onKeyFileChange, stacked = true)
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_fingerprint),
                value = fingerprint.ifBlank { "—" },
                onValueChange = {},
                readOnly = true,
                trailing = { TextButton(onClick = { fpExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) } },
            )
            DropdownMenu(expanded = fpExpanded, onDismissRequest = { fpExpanded = false }) {
                REALITY_FINGERPRINTS.forEach { fp -> DropdownMenuItem(text = { Text(fp) }, onClick = { onFingerprintChange(fp); fpExpanded = false }) }
            }
        }
    }
    // ALPN chips
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("http/1.1", "h2", "h3").forEach { tag ->
            FilterChip(
                selected = tag in alpn,
                onClick = { onToggleAlpn(tag) },
                label = { Text(tag) },
            )
        }
    }
}

@Composable
private fun RealityForm(
    dest: String,
    serverNames: String,
    privateKey: String,
    publicKey: String,
    shortIds: String,
    fingerprint: String,
    onDestChange: (String) -> Unit,
    onServerNamesChange: (String) -> Unit,
    onPrivateKeyChange: (String) -> Unit,
    onPublicKeyChange: (String) -> Unit,
    onShortIdsChange: (String) -> Unit,
    onFingerprintChange: (String) -> Unit,
    onGenerateX25519: () -> Unit,
) {
    var fpExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_reality_settings))
    GroupCard {
        FieldRow(label = stringResource(R.string.add_inbound_field_dest), value = dest, onValueChange = onDestChange, stacked = true, topDivider = false)
        FieldRow(
            label = stringResource(R.string.add_inbound_field_server_names),
            value = serverNames,
            onValueChange = onServerNamesChange,
            stacked = true,
            singleLineValue = false,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_private_key),
            value = privateKey,
            onValueChange = onPrivateKeyChange,
            monoValue = true,
            stacked = true,
            trailing = {
                TextButton(onClick = onGenerateX25519) {
                    Text(stringResource(R.string.add_inbound_generate_x25519), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            },
        )
        FieldRow(label = stringResource(R.string.add_inbound_field_public_key), value = publicKey, onValueChange = onPublicKeyChange, monoValue = true, stacked = true)
        FieldRow(
            label = stringResource(R.string.add_inbound_field_short_ids),
            value = shortIds,
            onValueChange = onShortIdsChange,
            monoValue = true,
            stacked = true,
            singleLineValue = false,
        )
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_fingerprint),
                value = fingerprint,
                onValueChange = {},
                readOnly = true,
                trailing = { TextButton(onClick = { fpExpanded = true }) { Text(stringResource(R.string.add_inbound_change)) } },
            )
            DropdownMenu(expanded = fpExpanded, onDismissRequest = { fpExpanded = false }) {
                REALITY_FINGERPRINTS.forEach { fp ->
                    DropdownMenuItem(text = { Text(fp) }, onClick = { onFingerprintChange(fp); fpExpanded = false })
                }
            }
        }
    }
}
