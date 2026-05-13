package com.firesin.xuipanel.feature.stats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.feature.stats.R

private const val DISABLED_ALPHA = 0.6f
private val OnlineColor = Color(0xFF4CAF50)
private val OnlineDotSize = 8.dp

@Composable
fun ClientRow(
    client: ClientStatDto,
    online: Boolean,
    showOnlineDot: Boolean,
    onClient: (emailKey: String, clientLabel: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val rowAlpha = if (client.enable) 1f else DISABLED_ALPHA
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClient(client.email, client.email) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Email + online dot
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showOnlineDot) {
                    Box(
                        modifier = Modifier
                            .size(OnlineDotSize)
                            .background(
                                color = if (online) OnlineColor else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = client.email,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Traffic columns: split (↑up ↓down) on top, used-of-total below, expiry under
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "↑ ${prettyBytes(client.up)}   ↓ ${prettyBytes(client.down)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val limitText = if (client.total > 0) {
                    stringResource(
                        R.string.stats_client_used_of_total,
                        prettyBytes(client.up + client.down),
                        prettyBytes(client.total),
                    )
                } else {
                    stringResource(
                        R.string.stats_client_used_only,
                        prettyBytes(client.up + client.down),
                    )
                }
                Text(
                    text = limitText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExpiryText(expiryMs = client.expiryTime)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientRowPreview() {
    XuiPanelTheme {
        ClientRow(
            client = ClientStatDto(
                id = 1,
                inboundId = 1,
                enable = true,
                email = "user@example.com",
                up = 500_000_000L,
                down = 2_000_000_000L,
                expiryTime = System.currentTimeMillis() + 5 * 86_400_000L,
                total = 10_000_000_000L,
                reset = 0L,
            ),
            online = true,
            showOnlineDot = true,
        )
    }
}
