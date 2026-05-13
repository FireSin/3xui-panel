package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.xui.util.randomShadowsocksPassword
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.ShadowsocksClientState

private val SS_METHODS = listOf(
    "chacha20-ietf-poly1305",
    "aes-256-gcm",
    "2022-blake3-aes-128-gcm",
    "2022-blake3-aes-256-gcm",
    "2022-blake3-chacha20-poly1305",
    "none",
)

private val SS_NETWORKS = listOf("tcp,udp", "tcp", "udp")

@Composable
fun ShadowsocksForm(
    method: String,
    password: String,
    network: String,
    clients: List<ShadowsocksClientState>,
    onMethodChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNetworkChange: (String) -> Unit,
    onAddClient: () -> Unit,
    onRemoveClient: (Int) -> Unit,
    onUpdateClient: (Int, ShadowsocksClientState.() -> ShadowsocksClientState) -> Unit,
) {
    var methodExpanded by remember { mutableStateOf(false) }
    var networkExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_method),
                value = method,
                onValueChange = {},
                readOnly = true,
                topDivider = false,
                trailing = {
                    TextButton(onClick = { methodExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                SS_METHODS.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { onMethodChange(m); methodExpanded = false })
                }
            }
        }
        FieldRow(
            label = stringResource(R.string.add_inbound_field_password),
            value = password,
            onValueChange = onPasswordChange,
            monoValue = true,
            stacked = true,
            trailing = {
                TextButton(onClick = { onPasswordChange(randomShadowsocksPassword()) }) {
                    Text(stringResource(R.string.add_inbound_regenerate), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_network),
                value = network,
                onValueChange = {},
                readOnly = true,
                trailing = {
                    TextButton(onClick = { networkExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            DropdownMenu(expanded = networkExpanded, onDismissRequest = { networkExpanded = false }) {
                SS_NETWORKS.forEach { n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { onNetworkChange(n); networkExpanded = false })
                }
            }
        }
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
                IconButton(onClick = { onRemoveClient(index) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.add_inbound_remove_client))
                }
            }
            FieldRow(
                label = stringResource(R.string.add_inbound_field_email),
                value = client.email,
                onValueChange = { onUpdateClient(index) { copy(email = it) } },
                stacked = true,
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_password),
                value = client.password,
                onValueChange = { onUpdateClient(index) { copy(password = it) } },
                monoValue = true,
                stacked = true,
                trailing = {
                    TextButton(onClick = { onUpdateClient(index) { copy(password = randomShadowsocksPassword()) } }) {
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
