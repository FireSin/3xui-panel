package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState

@Composable
fun SocksForm(
    auth: String,
    accounts: List<UserPassState>,
    udp: Boolean,
    ip: String,
    onAuthChange: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (Int) -> Unit,
    onUpdateAccount: (Int, UserPassState.() -> UserPassState) -> Unit,
    onUdpChange: (Boolean) -> Unit,
    onIpChange: (String) -> Unit,
) {
    var authExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_auth),
                value = auth,
                onValueChange = {},
                readOnly = true,
                topDivider = false,
                trailing = {
                    TextButton(onClick = { authExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            DropdownMenu(expanded = authExpanded, onDismissRequest = { authExpanded = false }) {
                listOf("noauth", "password").forEach { a ->
                    DropdownMenuItem(text = { Text(a) }, onClick = { onAuthChange(a); authExpanded = false })
                }
            }
        }
        FieldRow(
            label = stringResource(R.string.add_inbound_field_udp),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = udp, onCheckedChange = onUdpChange) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_ip),
            value = ip,
            onValueChange = onIpChange,
        )
    }

    if (auth == "password") {
        SectionHeader(stringResource(R.string.add_inbound_section_accounts))
        accounts.forEachIndexed { index, acc ->
            GroupCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.add_inbound_account_index, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemoveAccount(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
                FieldRow(
                    label = stringResource(R.string.add_inbound_field_user),
                    value = acc.user,
                    onValueChange = { onUpdateAccount(index) { copy(user = it) } },
                )
                FieldRow(
                    label = stringResource(R.string.add_inbound_field_pass),
                    value = acc.pass,
                    onValueChange = { onUpdateAccount(index) { copy(pass = it) } },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onAddAccount) { Text(stringResource(R.string.add_inbound_add_account)) }
        }
    }
}
