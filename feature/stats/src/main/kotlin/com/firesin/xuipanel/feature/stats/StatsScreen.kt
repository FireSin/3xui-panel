package com.firesin.xuipanel.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.stats.ui.InboundCard
import com.firesin.xuipanel.feature.stats.ui.ServerSummaryCard

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()

    StatsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        range = range,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onToggleExpanded = viewModel::toggleExpanded,
        onRangeChange = viewModel::setRange,
        chartFlow = viewModel::chartFlow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(
    uiState: StatsUiState,
    isRefreshing: Boolean,
    range: ChartRange,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleExpanded: (Int) -> Unit,
    onRangeChange: (ChartRange) -> Unit,
    chartFlow: (panelId: String, inboundId: Int) -> kotlinx.coroutines.flow.Flow<List<com.firesin.xuipanel.core.data.repository.DailyPoint>>,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats_title)) },
            )
        },
    ) { padding ->
        when (uiState) {
            is StatsUiState.NoActivePanel -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.stats_no_active_panel),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is StatsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is StatsUiState.Error -> StatsErrorState(
                error = uiState.error,
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is StatsUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ServerSummaryCard(summary = uiState.summary)
                    }
                    item {
                        RangeToggle(
                            selected = range,
                            onSelect = onRangeChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!uiState.onlinesAvailable) {
                        item {
                            Text(
                                text = stringResource(R.string.stats_online_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(uiState.inbounds, key = { it.id }) { inbound ->
                        val expanded = inbound.id in uiState.expandedIds
                        val chartPoints by chartFlow(uiState.panel.id, inbound.id)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        InboundCard(
                            inbound = inbound,
                            expanded = expanded,
                            onlineEmails = uiState.onlineEmails,
                            onlinesAvailable = uiState.onlinesAvailable,
                            onToggle = { onToggleExpanded(inbound.id) },
                            chartPoints = chartPoints,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeToggle(
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
                label = { Text(text = stringResource(option.labelRes())) },
            )
        }
    }
}

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.D7 -> R.string.stats_chart_range_7d
    ChartRange.D30 -> R.string.stats_chart_range_30d
    ChartRange.D90 -> R.string.stats_chart_range_90d
}

@Composable
private fun StatsErrorState(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error.toUserMessage(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.stats_retry))
        }
    }
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.stats_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.stats_error_tls, message)
    is DomainError.Network -> stringResource(R.string.stats_error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.stats_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.stats_error_response, body)
    is DomainError.Unexpected -> stringResource(R.string.stats_error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.stats_error_pin_mismatch)
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenLoadingPreview() {
    XuiPanelTheme {
        StatsContent(
            uiState = StatsUiState.Loading,
            isRefreshing = false,
            range = ChartRange.D7,
            onRefresh = {},
            onRetry = {},
            onToggleExpanded = {},
            onRangeChange = {},
            chartFlow = { _, _ -> kotlinx.coroutines.flow.flowOf(emptyList()) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenNoActivePanelPreview() {
    XuiPanelTheme {
        StatsContent(
            uiState = StatsUiState.NoActivePanel,
            isRefreshing = false,
            range = ChartRange.D7,
            onRefresh = {},
            onRetry = {},
            onToggleExpanded = {},
            onRangeChange = {},
            chartFlow = { _, _ -> kotlinx.coroutines.flow.flowOf(emptyList()) },
        )
    }
}
