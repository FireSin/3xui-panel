package com.firesin.xuipanel.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.stats.chart.InboundTrafficChart

@Composable
fun ClientStatsScreen(
    onBack: () -> Unit,
    viewModel: ClientStatsViewModel = hiltViewModel(),
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val chartPoints by viewModel.chartFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ClientStatsContent(
        clientLabel = viewModel.clientLabel,
        range = range,
        chartPoints = chartPoints,
        onRangeChange = viewModel::setRange,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientStatsContent(
    clientLabel: String,
    range: ChartRange,
    chartPoints: List<DailyPoint>,
    onRangeChange: (ChartRange) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = clientLabel) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_client_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ClientRangeToggle(
                selected = range,
                onSelect = onRangeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            InboundTrafficChart(
                points = chartPoints,
                modifier = Modifier.height(220.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientRangeToggle(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ChartRange.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(text = stringResource(chartRangeLabelRes(option))) },
            )
        }
    }
}

private fun chartRangeLabelRes(range: ChartRange): Int = when (range) {
    ChartRange.D7 -> R.string.stats_chart_range_7d
    ChartRange.D30 -> R.string.stats_chart_range_30d
    ChartRange.D90 -> R.string.stats_chart_range_90d
}

@Preview(showBackground = true)
@Composable
private fun ClientStatsContentPreview() {
    val msPerDay = 86_400_000L
    val today = (System.currentTimeMillis() / msPerDay) * msPerDay
    XuiPanelTheme {
        ClientStatsContent(
            clientLabel = "user@example.com",
            range = ChartRange.D7,
            chartPoints = listOf(
                DailyPoint(today - 2 * msPerDay, 10_000_000L, 50_000_000L),
                DailyPoint(today - msPerDay, 20_000_000L, 80_000_000L),
                DailyPoint(today, 5_000_000L, 30_000_000L),
            ),
            onRangeChange = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientStatsContentEmptyPreview() {
    XuiPanelTheme {
        ClientStatsContent(
            clientLabel = "user@example.com",
            range = ChartRange.D7,
            chartPoints = emptyList(),
            onRangeChange = {},
            onBack = {},
        )
    }
}
