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
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.Hy2ClientState
import com.firesin.xuipanel.feature.inbounds.add.ui.randomShortId

@Composable
fun Hysteria2Form(
    obfsEnabled: Boolean,
    obfsPassword: String,
    ignoreClientBandwidth: Boolean,
    clients: List<Hy2ClientState>,
    onObfsEnabledChange: (Boolean) -> Unit,
    onObfsPasswordChange: (String) -> Unit,
    onIgnoreClientBandwidthChange: (Boolean) -> Unit,
    onAddClient: () -> Unit,
    onRemoveClient: (Int) -> Unit,
    onUpdateClient: (Int, Hy2ClientState.() -> Hy2ClientState) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_obfs_enabled),
            value = "",
            onValueChange = {},
            readOnly = true,
            topDivider = false,
            trailing = { IosToggle(checked = obfsEnabled, onCheckedChange = onObfsEnabledChange) },
        )
        if (obfsEnabled) {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_obfs_password),
                value = obfsPassword,
                onValueChange = onObfsPasswordChange,
                monoValue = true,
                stacked = true,
                trailing = {
                    TextButton(onClick = { onObfsPasswordChange(randomShortId() + randomShortId() + randomShortId() + randomShortId()) }) {
                        Text(stringResource(R.string.add_inbound_regenerate), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        }
        FieldRow(
            label = stringResource(R.string.add_inbound_field_ignore_client_bandwidth),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = {
                IosToggle(checked = ignoreClientBandwidth, onCheckedChange = onIgnoreClientBandwidthChange)
            },
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
                label = stringResource(R.string.add_inbound_field_password),
                value = client.password,
                onValueChange = { onUpdateClient(index) { copy(password = it) } },
                monoValue = true,
                stacked = true,
                trailing = {
                    TextButton(onClick = { onUpdateClient(index) { copy(password = randomShortId() + randomShortId() + randomShortId() + randomShortId()) } }) {
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
