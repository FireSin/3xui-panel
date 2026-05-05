package com.firesin.xuipanel.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.stats.R
import com.firesin.xuipanel.feature.stats.ServerSummary

@Composable
fun ServerSummaryCard(
    summary: ServerSummary,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_server_summary),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryItem(
                    label = stringResource(R.string.stats_total_up),
                    value = prettyBytes(summary.totalUp),
                    modifier = Modifier.weight(1f),
                )
                SummaryItem(
                    label = stringResource(R.string.stats_total_down),
                    value = prettyBytes(summary.totalDown),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryItem(
                    label = stringResource(R.string.stats_inbound_count),
                    value = summary.inboundCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                SummaryItem(
                    label = stringResource(R.string.stats_active_clients),
                    value = summary.activeClientCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerSummaryCardPreview() {
    XuiPanelTheme {
        ServerSummaryCard(
            summary = ServerSummary(
                totalUp = 1_500_000_000L,
                totalDown = 5_000_000_000L,
                inboundCount = 3,
                activeClientCount = 12,
            ),
        )
    }
}
