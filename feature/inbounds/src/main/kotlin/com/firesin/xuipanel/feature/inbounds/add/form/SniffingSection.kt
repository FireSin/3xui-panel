package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SniffingSection(
    enabled: Boolean,
    destOverride: Set<String>,
    metadataOnly: Boolean,
    routeOnly: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onToggleDestOverride: (String) -> Unit,
    onMetadataOnlyChange: (Boolean) -> Unit,
    onRouteOnlyChange: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_sniffing))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_sniffing_enabled),
            value = "",
            onValueChange = {},
            readOnly = true,
            topDivider = false,
            trailing = { IosToggle(checked = enabled, onCheckedChange = onEnabledChange) },
        )
        if (enabled) {
            FieldRow(
                label = stringResource(R.string.add_inbound_field_metadata_only),
                value = "",
                onValueChange = {},
                readOnly = true,
                trailing = { IosToggle(checked = metadataOnly, onCheckedChange = onMetadataOnlyChange) },
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_route_only),
                value = "",
                onValueChange = {},
                readOnly = true,
                trailing = { IosToggle(checked = routeOnly, onCheckedChange = onRouteOnlyChange) },
            )
        }
    }
    if (enabled) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("http", "tls", "quic", "fakedns").forEach { tag ->
                FilterChip(
                    selected = tag in destOverride,
                    onClick = { onToggleDestOverride(tag) },
                    label = { Text(tag) },
                )
            }
        }
    }
}
