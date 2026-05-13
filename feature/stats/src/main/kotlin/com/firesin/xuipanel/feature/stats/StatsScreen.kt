package com.firesin.xuipanel.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import com.firesin.xuipanel.core.designsystem.component.EmptyState
import com.firesin.xuipanel.core.designsystem.component.ErrorState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.stats.chart.InboundTrafficChart
import com.firesin.xuipanel.feature.stats.ui.ClientRow
import com.firesin.xuipanel.feature.stats.ui.ExpiryText

private val HeroShape = RoundedCornerShape(18.dp)
private val ProgressShape = RoundedCornerShape(2.dp)
private const val PROGRESS_HEIGHT_DP = 4
private const val LEGEND_DOT_DP = 8
private const val TOP_CLIENTS_LIMIT = 5

@Composable
fun StatsScreen(
    onNavigateToClientStats: (panelId: String, inboundId: Int, emailKey: String, clientLabel: String) -> Unit = { _, _, _, _ -> },
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
        onNavigateToClientStats = onNavigateToClientStats,
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
    chartFlow: (panelId: String, inboundId: Int) -> kotlinx.coroutines.flow.Flow<List<DailyPoint>>,
    onNavigateToClientStats: (panelId: String, inboundId: Int, emailKey: String, clientLabel: String) -> Unit = { _, _, _, _ -> },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats_title)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.stats_retry),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            is StatsUiState.NoActivePanel -> EmptyState(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                title = stringResource(R.string.stats_no_active_panel_title),
                description = stringResource(R.string.stats_no_active_panel_description),
                modifier = Modifier.padding(padding),
            )

            is StatsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is StatsUiState.Error -> ErrorState(
                title = stringResource(R.string.stats_error_title),
                description = uiState.error.toUserMessage(),
                actionLabel = stringResource(R.string.stats_retry),
                onAction = onRetry,
                modifier = Modifier.padding(padding),
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
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // Range picker
                    item {
                        SegmentedPicker(
                            options = ChartRange.entries,
                            selected = range,
                            onSelect = onRangeChange,
                            label = { stringResource(it.labelRes()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    // Hero card
                    item {
                        StatsHeroCard(
                            summary = uiState.summary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // BY INBOUND section
                    item {
                        SectionHeader(title = stringResource(R.string.stats_section_by_inbound))
                    }

                    item {
                        val totalTraffic = uiState.inbounds.sumOf { it.up + it.down }
                        GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            uiState.inbounds.forEachIndexed { index, inbound ->
                                val expanded = inbound.id in uiState.expandedIds
                                val chartPoints by chartFlow(uiState.panel.id, inbound.id)
                                    .collectAsStateWithLifecycle(initialValue = emptyList())

                                InboundRow(
                                    inbound = inbound,
                                    expanded = expanded,
                                    totalTraffic = totalTraffic,
                                    onlineEmails = uiState.onlineEmails,
                                    onlinesAvailable = uiState.onlinesAvailable,
                                    chartPoints = chartPoints,
                                    topDivider = index > 0,
                                    onToggle = { onToggleExpanded(inbound.id) },
                                    onClient = { emailKey, clientLabel ->
                                        onNavigateToClientStats(
                                            uiState.panel.id,
                                            inbound.id,
                                            emailKey,
                                            clientLabel,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // TOP CLIENTS section
                    val topClients = uiState.inbounds
                        .flatMap { inbound ->
                            inbound.clientStats.orEmpty().map { cs ->
                                Pair(inbound.id, cs)
                            }
                        }
                        .sortedByDescending { (_, cs) -> cs.up + cs.down }
                        .take(TOP_CLIENTS_LIMIT)

                    if (topClients.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(R.string.stats_section_top_clients))
                        }

                        item {
                            GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                topClients.forEachIndexed { index, (inboundId, client) ->
                                    TopClientRow(
                                        client = client,
                                        topDivider = index > 0,
                                        onClick = {
                                            onNavigateToClientStats(
                                                uiState.panel.id,
                                                inboundId,
                                                client.email,
                                                client.email,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (!uiState.onlinesAvailable) {
                        item {
                            Text(
                                text = stringResource(R.string.stats_online_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsHeroCard(
    summary: ServerSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = HeroShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline, HeroShape),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Left: label + value
                Column {
                    Text(
                        text = stringResource(R.string.stats_total_period).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = prettyBytes(summary.totalUp + summary.totalDown),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Right: Up/Down legend dots
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LegendDot(
                        label = stringResource(R.string.stats_legend_up),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LegendDot(
                        label = stringResource(R.string.stats_legend_down),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(LEGEND_DOT_DP.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── InboundRow (expandable) inside GroupCard ──────────────────────────────────

@Composable
private fun InboundRow(
    inbound: InboundDto,
    expanded: Boolean,
    totalTraffic: Long,
    onlineEmails: Set<String>,
    onlinesAvailable: Boolean,
    chartPoints: List<DailyPoint>,
    topDivider: Boolean,
    onToggle: () -> Unit,
    onClient: (emailKey: String, clientLabel: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        // Header row: name + traffic + chevron
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inbound.remark.ifBlank { inbound.tag },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = prettyBytes(inbound.up + inbound.down),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        // Progress bar
        val pct = if (totalTraffic > 0) {
            ((inbound.up + inbound.down).toFloat() / totalTraffic.toFloat()).coerceIn(0f, 1f)
        } else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(PROGRESS_HEIGHT_DP.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = ProgressShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(PROGRESS_HEIGHT_DP.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = ProgressShape,
                    ),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Expanded: client list + chart
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

// ── TopClientRow ─────────────────────────────────────────────────────────────

@Composable
private fun TopClientRow(
    client: ClientStatDto,
    topDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (topDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = client.email,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = prettyBytes(client.up + client.down),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── ChartRange label ─────────────────────────────────────────────────────────

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.D7 -> R.string.stats_chart_range_7d
    ChartRange.D30 -> R.string.stats_chart_range_30d
    ChartRange.D90 -> R.string.stats_chart_range_90d
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

// ── Previews ─────────────────────────────────────────────────────────────────

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
