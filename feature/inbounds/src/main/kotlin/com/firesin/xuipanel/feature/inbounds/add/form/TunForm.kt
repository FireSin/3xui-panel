package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.inbounds.R

@Composable
fun TunForm(
    mtu: String,
    gso: Boolean,
    gro: Boolean,
    enableExFilter: Boolean,
    strictRoute: Boolean,
    routeAddress: String,
    routeAddressSet: String,
    routeExcludeAddress: String,
    routeExcludeAddressSet: String,
    onMtuChange: (String) -> Unit,
    onGsoChange: (Boolean) -> Unit,
    onGroChange: (Boolean) -> Unit,
    onEnableExFilterChange: (Boolean) -> Unit,
    onStrictRouteChange: (Boolean) -> Unit,
    onRouteAddressChange: (String) -> Unit,
    onRouteAddressSetChange: (String) -> Unit,
    onRouteExcludeAddressChange: (String) -> Unit,
    onRouteExcludeAddressSetChange: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_mtu),
            value = mtu,
            onValueChange = onMtuChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            topDivider = false,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_gso),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = gso, onCheckedChange = onGsoChange) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_gro),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = gro, onCheckedChange = onGroChange) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_enable_ex_filter),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = enableExFilter, onCheckedChange = onEnableExFilterChange) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_strict_route),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = { IosToggle(checked = strictRoute, onCheckedChange = onStrictRouteChange) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_route_address),
            value = routeAddress,
            onValueChange = onRouteAddressChange,
            stacked = true,
            placeholder = stringResource(R.string.add_inbound_hint_comma_separated),
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_route_address_set),
            value = routeAddressSet,
            onValueChange = onRouteAddressSetChange,
            stacked = true,
            placeholder = stringResource(R.string.add_inbound_hint_comma_separated),
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_route_exclude_address),
            value = routeExcludeAddress,
            onValueChange = onRouteExcludeAddressChange,
            stacked = true,
            placeholder = stringResource(R.string.add_inbound_hint_comma_separated),
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_tun_route_exclude_address_set),
            value = routeExcludeAddressSet,
            onValueChange = onRouteExcludeAddressSetChange,
            stacked = true,
            placeholder = stringResource(R.string.add_inbound_hint_comma_separated),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TunFormPreview() {
    XuiPanelTheme {
        TunForm(
            mtu = "1500",
            gso = false,
            gro = false,
            enableExFilter = false,
            strictRoute = true,
            routeAddress = "",
            routeAddressSet = "",
            routeExcludeAddress = "",
            routeExcludeAddressSet = "",
            onMtuChange = {},
            onGsoChange = {},
            onGroChange = {},
            onEnableExFilterChange = {},
            onStrictRouteChange = {},
            onRouteAddressChange = {},
            onRouteAddressSetChange = {},
            onRouteExcludeAddressChange = {},
            onRouteExcludeAddressSetChange = {},
        )
    }
}
