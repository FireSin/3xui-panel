package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.HysteriaClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.randomShortId

@Composable
fun HysteriaForm(
    obfsPassword: String,
    udpIdleTimeout: String,
    tlsServerName: String,
    tlsCertificateFile: String,
    tlsKeyFile: String,
    clients: List<HysteriaClientState>,
    onObfsPasswordChange: (String) -> Unit,
    onUdpIdleTimeoutChange: (String) -> Unit,
    onTlsServerNameChange: (String) -> Unit,
    onTlsCertificateFileChange: (String) -> Unit,
    onTlsKeyFileChange: (String) -> Unit,
    onAddClient: () -> Unit,
    onRemoveClient: (Int) -> Unit,
    onUpdateClient: (Int, HysteriaClientState.() -> HysteriaClientState) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_hysteria_obfs_password),
            value = obfsPassword,
            onValueChange = onObfsPasswordChange,
            monoValue = true,
            stacked = true,
            topDivider = false,
            trailing = {
                TextButton(onClick = { onObfsPasswordChange(randomShortId() + randomShortId() + randomShortId() + randomShortId()) }) {
                    Text(stringResource(R.string.add_inbound_regenerate), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_hysteria_udp_idle_timeout),
            value = udpIdleTimeout,
            onValueChange = onUdpIdleTimeoutChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }

    SectionHeader(stringResource(R.string.add_inbound_section_tls_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_server_name),
            value = tlsServerName,
            onValueChange = onTlsServerNameChange,
            stacked = true,
            topDivider = false,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_hysteria_alpn_hint),
            value = "h3",
            onValueChange = {},
            readOnly = true,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_cert_file),
            value = tlsCertificateFile,
            onValueChange = onTlsCertificateFileChange,
            stacked = true,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_key_file),
            value = tlsKeyFile,
            onValueChange = onTlsKeyFileChange,
            stacked = true,
        )
    }

    SectionHeader(stringResource(R.string.add_inbound_section_clients))
    clients.forEachIndexed { index, client ->
        GroupCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_inbound_client_index, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (clients.size > 1) {
                    IconButton(onClick = { onRemoveClient(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.add_inbound_remove_client))
                    }
                }
            }
            FieldRow(
                label = stringResource(R.string.add_inbound_field_email),
                value = client.email,
                onValueChange = { onUpdateClient(index) { copy(email = it) } },
                stacked = true,
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_auth),
                value = client.auth,
                onValueChange = { onUpdateClient(index) { copy(auth = it) } },
                monoValue = true,
                stacked = true,
                trailing = {
                    TextButton(onClick = { onUpdateClient(index) { copy(auth = randomShortId() + randomShortId()) } }) {
                        Text(stringResource(R.string.add_inbound_regenerate), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_total_gb),
                value = client.totalGb,
                onValueChange = { onUpdateClient(index) { copy(totalGb = it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_enable),
                value = "",
                onValueChange = {},
                readOnly = true,
                trailing = {
                    IosToggle(
                        checked = client.enable,
                        onCheckedChange = { onUpdateClient(index) { copy(enable = it) } },
                    )
                },
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onAddClient) { Text(stringResource(R.string.add_inbound_add_client)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun HysteriaFormPreview() {
    XuiPanelTheme {
        HysteriaForm(
            obfsPassword = "obfs-secret",
            udpIdleTimeout = "60",
            tlsServerName = "example.com",
            tlsCertificateFile = "",
            tlsKeyFile = "",
            clients = listOf(HysteriaClientState(auth = "abc123", email = "user@example.com")),
            onObfsPasswordChange = {},
            onUdpIdleTimeoutChange = {},
            onTlsServerNameChange = {},
            onTlsCertificateFileChange = {},
            onTlsKeyFileChange = {},
            onAddClient = {},
            onRemoveClient = {},
            onUpdateClient = { _, _ -> },
        )
    }
}
