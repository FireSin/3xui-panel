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
import com.firesin.xuipanel.core.xui.util.randomUuid
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.TrojanClientState

private val TROJAN_FLOWS = listOf("" to "none", "xtls-rprx-vision" to "xtls-rprx-vision")

@Composable
fun TrojanForm(
    clients: List<TrojanClientState>,
    onAddClient: () -> Unit,
    onRemoveClient: (Int) -> Unit,
    onUpdateClient: (Int, TrojanClientState.() -> TrojanClientState) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_clients))
    clients.forEachIndexed { index, client ->
        var flowExpanded by remember { mutableStateOf(false) }
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
                label = stringResource(R.string.add_inbound_field_password),
                value = client.password,
                onValueChange = { onUpdateClient(index) { copy(password = it) } },
                monoValue = true,
                stacked = true,
                trailing = {
                    TextButton(onClick = { onUpdateClient(index) { copy(password = randomUuid()) } }) {
                        Text(stringResource(R.string.add_inbound_regenerate), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            Box {
                FieldRow(
                    label = stringResource(R.string.add_inbound_field_flow),
                    value = client.flow.ifBlank { "none" },
                    onValueChange = {},
                    readOnly = true,
                    trailing = {
                        TextButton(onClick = { flowExpanded = true }) {
                            Text(stringResource(R.string.add_inbound_change), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                )
                DropdownMenu(expanded = flowExpanded, onDismissRequest = { flowExpanded = false }) {
                    TROJAN_FLOWS.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onUpdateClient(index) { copy(flow = value) }
                                flowExpanded = false
                            },
                        )
                    }
                }
            }
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
