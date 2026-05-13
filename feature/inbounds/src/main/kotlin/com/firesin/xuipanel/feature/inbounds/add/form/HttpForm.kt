package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.UserPassState

@Composable
fun HttpForm(
    accounts: List<UserPassState>,
    allowTransparent: Boolean,
    onAllowTransparentChange: (Boolean) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (Int) -> Unit,
    onUpdateAccount: (Int, UserPassState.() -> UserPassState) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_allow_transparent),
            value = "",
            onValueChange = {},
            readOnly = true,
            topDivider = false,
            trailing = { IosToggle(checked = allowTransparent, onCheckedChange = onAllowTransparentChange) },
        )
    }
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
