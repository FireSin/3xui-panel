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
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import com.firesin.xuipanel.feature.inbounds.add.ui.WgPeerState

@Composable
fun WireguardForm(
    secretKey: String,
    mtu: String,
    peers: List<WgPeerState>,
    onSecretKeyChange: (String) -> Unit,
    onMtuChange: (String) -> Unit,
    onAddPeer: () -> Unit,
    onRemovePeer: (Int) -> Unit,
    onUpdatePeer: (Int, WgPeerState.() -> WgPeerState) -> Unit,
) {
    SectionHeader(stringResource(R.string.add_inbound_section_protocol_settings))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_secret_key),
            value = secretKey,
            onValueChange = onSecretKeyChange,
            monoValue = true,
            stacked = true,
            topDivider = false,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_mtu),
            value = mtu,
            onValueChange = onMtuChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    SectionHeader(stringResource(R.string.add_inbound_section_peers))
    peers.forEachIndexed { index, peer ->
        GroupCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_inbound_peer_index, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemovePeer(index) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            FieldRow(
                label = stringResource(R.string.add_inbound_field_public_key),
                value = peer.publicKey,
                onValueChange = { onUpdatePeer(index) { copy(publicKey = it) } },
                monoValue = true,
                stacked = true,
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_allowed_ips),
                value = peer.allowedIPs,
                onValueChange = { onUpdatePeer(index) { copy(allowedIPs = it) } },
                stacked = true,
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_preshared_key),
                value = peer.presharedKey,
                onValueChange = { onUpdatePeer(index) { copy(presharedKey = it) } },
                monoValue = true,
                stacked = true,
            )
            FieldRow(
                label = stringResource(R.string.add_inbound_field_keep_alive),
                value = peer.keepAlive,
                onValueChange = { onUpdatePeer(index) { copy(keepAlive = it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onAddPeer) { Text(stringResource(R.string.add_inbound_add_peer)) }
    }
}
