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

@Composable
fun DokodemoForm(
    address: String,
    targetPort: String,
    network: String,
    followRedirect: Boolean,
    onAddressChange: (String) -> Unit,
    onTargetPortChange: (String) -> Unit,
    onNetworkChange: (String) -> Unit,
    onFollowRedirectChange: (Boolean) -> Unit,
) {
    var networkExpanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_address),
            value = address,
            onValueChange = onAddressChange,
            stacked = true,
            topDivider = false,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_target_port),
            value = targetPort,
            onValueChange = onTargetPortChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_network),
                value = network,
                onValueChange = {},
                readOnly = true,
                trailing = {
                    TextButton(onClick = { networkExpanded = true }) {
                        Text(stringResource(R.string.add_inbound_change))
                    }
                },
            )
            DropdownMenu(expanded = networkExpanded, onDismissRequest = { networkExpanded = false }) {
                listOf("tcp", "udp", "tcp,udp").forEach { n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { onNetworkChange(n); networkExpanded = false })
                }
            }
        }
        FieldRow(
            label = stringResource(R.string.add_inbound_field_follow_redirect),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = followRedirect, onCheckedChange = onFollowRedirectChange) },
        )
    }
}
