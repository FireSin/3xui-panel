package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.ProtocolType

@Composable
fun ProtocolPickerSection(
    selected: ProtocolType,
    onSelected: (ProtocolType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_protocol))
    GroupCard {
        Box {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_protocol),
                value = selected.label,
                onValueChange = {},
                readOnly = true,
                topDivider = false,
                trailing = {
                    androidx.compose.material3.TextButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.add_inbound_change))
                    }
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ProtocolType.entries.forEach { proto ->
                    DropdownMenuItem(
                        text = { Text(proto.label) },
                        onClick = {
                            onSelected(proto)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
