package com.firesin.xuipanel.feature.nodes

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.designsystem.chart.HistoryLineChart
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
import com.firesin.xuipanel.feature.nodes.ui.NodeDetailUiState
import com.firesin.xuipanel.feature.nodes.ui.NodeDetailViewModel
import com.firesin.xuipanel.feature.nodes.ui.NodeHistoryMetric

@Composable
fun NodeDetailScreen(
    onNavigateBack: () -> Unit = {},
    onEditNode: () -> Unit = {},
    viewModel: NodeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NodeDetailContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEditNode = onEditNode,
        onRefresh = viewModel::refresh,
        onMetricSelect = viewModel::selectMetric,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDetailContent(
    uiState: NodeDetailUiState,
    onNavigateBack: () -> Unit = {},
    onEditNode: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onMetricSelect: (NodeHistoryMetric) -> Unit = {},
) {
    val nodeName = when (uiState) {
        is NodeDetailUiState.Content -> uiState.node.displayName()
        else -> stringResource(R.string.nodes_detail_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = nodeName,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nodes_detail_cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.nodes_action_refresh),
                        )
                    }
                    IconButton(onClick = onEditNode) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.nodes_action_edit),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (uiState) {
            is NodeDetailUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is NodeDetailUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is NodeDetailUiState.Content -> NodeDetailBody(
                uiState = uiState,
                onMetricSelect = onMetricSelect,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun NodeDetailBody(
    uiState: NodeDetailUiState.Content,
    onMetricSelect: (NodeHistoryMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { NodeInfoCard(node = uiState.node) }

        item {
            SectionHeader(stringResource(R.string.nodes_detail_section_history))
        }

        item {
            NodeHistoryCard(
                selectedMetric = uiState.selectedMetric,
                histories = uiState.histories,
                isLoading = uiState.isHistoryLoading,
                onMetricSelect = onMetricSelect,
            )
        }
    }
}

@Composable
private fun NodeInfoCard(node: NodeDto, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${node.scheme}://${node.address}:${node.port}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (node.remark.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = node.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.nodes_detail_info_stats,
                    node.latencyMs,
                    node.cpuPct,
                    node.memPct,
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (node.xrayVersion.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.nodes_detail_xray_version, node.xrayVersion),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.lastError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = node.lastError,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun NodeHistoryCard(
    selectedMetric: NodeHistoryMetric,
    histories: Map<NodeHistoryMetric, List<ServerHistoryPointDto>>,
    isLoading: Boolean,
    onMetricSelect: (NodeHistoryMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            SegmentedPicker(
                options = NodeHistoryMetric.entries,
                selected = selectedMetric,
                onSelect = onMetricSelect,
                label = { nodeMetricLabel(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            val points = histories[selectedMetric].orEmpty()

            if (isLoading && points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HistoryLineChart(
                    values = points.map { it.v },
                    noDataLabel = stringResource(R.string.nodes_detail_no_data),
                )
                Spacer(Modifier.height(6.dp))
                NodeHistorySummary(metric = selectedMetric, points = points)
            }
        }
    }
}

@Composable
private fun nodeMetricLabel(metric: NodeHistoryMetric): String = when (metric) {
    NodeHistoryMetric.CPU -> stringResource(R.string.nodes_detail_metric_cpu)
    NodeHistoryMetric.MEM -> stringResource(R.string.nodes_detail_metric_mem)
    NodeHistoryMetric.NET_IN -> stringResource(R.string.nodes_detail_metric_net_in)
    NodeHistoryMetric.NET_OUT -> stringResource(R.string.nodes_detail_metric_net_out)
    NodeHistoryMetric.LATENCY -> stringResource(R.string.nodes_detail_metric_latency)
    NodeHistoryMetric.ONLINE -> stringResource(R.string.nodes_detail_metric_online)
}

@Composable
private fun NodeHistorySummary(metric: NodeHistoryMetric, points: List<ServerHistoryPointDto>) {
    if (points.size < 2) return
    val values = points.map { it.v }
    val min = values.min()
    val max = values.max()
    val avg = values.average()
    Text(
        text = stringResource(
            R.string.nodes_detail_history_summary,
            formatNodeMetric(metric, min),
            formatNodeMetric(metric, avg),
            formatNodeMetric(metric, max),
        ),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatNodeMetric(metric: NodeHistoryMetric, value: Double): String = when (metric) {
    NodeHistoryMetric.CPU -> "%.1f%%".format(value)
    NodeHistoryMetric.MEM -> prettyBytes(value.toLong())
    NodeHistoryMetric.NET_IN, NodeHistoryMetric.NET_OUT -> "${prettyBytes(value.toLong())}/s"
    NodeHistoryMetric.LATENCY -> "%.0f ms".format(value)
    NodeHistoryMetric.ONLINE -> "%.0f".format(value)
}

private fun NodeDto.displayName(): String = name.ifBlank { "node-$id" }

@Preview(showBackground = true)
@Composable
private fun NodeDetailContentPreview() {
    XuiPanelTheme {
        NodeDetailContent(
            uiState = NodeDetailUiState.Content(
                node = NodeDto(
                    id = 1,
                    name = "Frankfurt Edge",
                    remark = "backup node",
                    scheme = "https",
                    address = "fra.example.com",
                    port = 2053,
                    status = "online",
                    latencyMs = 42,
                    cpuPct = 12.5,
                    memPct = 38.0,
                    uptimeSecs = 86400L,
                    xrayVersion = "25.4.0",
                ),
                histories = mapOf(
                    NodeHistoryMetric.CPU to listOf(
                        ServerHistoryPointDto(t = 0, v = 10.0),
                        ServerHistoryPointDto(t = 120, v = 15.0),
                        ServerHistoryPointDto(t = 240, v = 12.0),
                        ServerHistoryPointDto(t = 360, v = 20.0),
                        ServerHistoryPointDto(t = 480, v = 18.0),
                    ),
                ),
                isHistoryLoading = false,
                selectedMetric = NodeHistoryMetric.CPU,
            ),
        )
    }
}
