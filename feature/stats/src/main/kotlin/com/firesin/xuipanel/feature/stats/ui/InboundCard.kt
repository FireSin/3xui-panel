package com.firesin.xuipanel.feature.stats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.stats.chart.InboundTrafficChart

@Composable
fun InboundCard(
    inbound: InboundDto,
    expanded: Boolean,
    onlineEmails: Set<String>,
    onlinesAvailable: Boolean,
    onToggle: () -> Unit,
    chartPoints: List<DailyPoint> = emptyList(),
    onClient: (emailKey: String, clientLabel: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inbound.remark.ifBlank { inbound.tag },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${inbound.protocol.uppercase()} · :${inbound.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            // Traffic info
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "${prettyBytes(inbound.up)} ↑ / ${prettyBytes(inbound.down)} ↓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (inbound.total > 0) {
                    Spacer(Modifier.height(4.dp))
                    val used = (inbound.up + inbound.down).coerceAtMost(inbound.total)
                    LinearProgressIndicator(
                        progress = { used.toFloat() / inbound.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${prettyBytes(used)} / ${prettyBytes(inbound.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpiryText(expiryMs = inbound.expiryTime)
                Spacer(Modifier.height(12.dp))
            }

            // Expanded client list + traffic chart
            AnimatedVisibility(visible = expanded) {
                Column {
                    inbound.clientStats.orEmpty().forEach { client ->
                        ClientRow(
                            client = client,
                            online = client.email in onlineEmails,
                            showOnlineDot = onlinesAvailable,
                            onClient = onClient,
                        )
                    }
                    InboundTrafficChart(
                        points = chartPoints,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InboundCardPreview() {
    XuiPanelTheme {
        InboundCard(
            inbound = InboundDto(
                id = 1,
                up = 200_000_000L,
                down = 800_000_000L,
                total = 2_000_000_000L,
                remark = "Main inbound",
                enable = true,
                expiryTime = 0L,
                clientStats = listOf(
                    ClientStatDto(
                        id = 1,
                        inboundId = 1,
                        enable = true,
                        email = "alice@example.com",
                        up = 100_000_000L,
                        down = 400_000_000L,
                        expiryTime = 0L,
                        total = 0L,
                        reset = 0L,
                    ),
                ),
                listen = "",
                port = 443,
                protocol = "vmess",
                settings = "{}",
                streamSettings = "{}",
                tag = "inbound-1",
                sniffing = "{}",
            ),
            expanded = true,
            onlineEmails = setOf("alice@example.com"),
            onlinesAvailable = true,
            onToggle = {},
        )
    }
}
