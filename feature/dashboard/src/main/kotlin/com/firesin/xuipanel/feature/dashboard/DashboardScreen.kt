package com.firesin.xuipanel.feature.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.util.formatSpeed
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.common.util.secondsToCompact
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.component.EmptyState
import com.firesin.xuipanel.core.designsystem.component.ErrorState
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.GroupRow
import com.firesin.xuipanel.core.designsystem.component.RingStat
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.component.SpeedColumn
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.MemDto
import com.firesin.xuipanel.core.xui.dto.NetIoDto
import com.firesin.xuipanel.core.xui.dto.NetTrafficDto
import com.firesin.xuipanel.core.xui.dto.PublicIpDto
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.XrayStatusDto
import com.firesin.xuipanel.feature.dashboard.ui.DashboardUiState
import com.firesin.xuipanel.feature.dashboard.ui.DashboardViewModel
import com.firesin.xuipanel.feature.dashboard.ui.HistoryMetric
import java.time.Instant

private val HeroCardShape = RoundedCornerShape(18.dp)

@Composable
fun DashboardScreen(
    onAddPanel: () -> Unit = {},
    onNavigateToStats: () -> Unit,
    onNavigateToInbounds: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val actionEvent by viewModel.actionEvent.collectAsStateWithLifecycle()
    val isActionInFlight by viewModel.isActionInFlight.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val restartSuccessMessage = stringResource(R.string.dashboard_action_restart_success)
    val stopSuccessMessage = stringResource(R.string.dashboard_action_stop_success)
    val errorMessage = actionEvent?.let {
        if (it is DashboardViewModel.ActionEvent.Failure) it.error.toUserMessage() else null
    }

    LaunchedEffect(actionEvent) {
        when (val ev = actionEvent) {
            DashboardViewModel.ActionEvent.RestartSuccess -> {
                snackbarHostState.showSnackbar(restartSuccessMessage)
                viewModel.actionEventShown()
            }
            DashboardViewModel.ActionEvent.StopSuccess -> {
                snackbarHostState.showSnackbar(stopSuccessMessage)
                viewModel.actionEventShown()
            }
            is DashboardViewModel.ActionEvent.Failure -> {
                snackbarHostState.showSnackbar(errorMessage ?: ev.error.toString())
                viewModel.actionEventShown()
            }
            null -> Unit
        }
    }

    DashboardContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        isActionInFlight = isActionInFlight,
        onRefresh = viewModel::refresh,
        onAddPanel = onAddPanel,
        onNavigateToStats = onNavigateToStats,
        onNavigateToInbounds = onNavigateToInbounds,
        onMenuClick = onMenuClick,
        onRestartXray = viewModel::restartXray,
        onStopXray = viewModel::stopXray,
        onNavigateToLogs = onNavigateToLogs,
        selectedMetric = selectedMetric,
        history = history,
        isHistoryLoading = isHistoryLoading,
        onMetricSelect = viewModel::selectMetric,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    isActionInFlight: Boolean,
    onRefresh: () -> Unit,
    onAddPanel: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToInbounds: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onRestartXray: () -> Unit = {},
    onStopXray: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    selectedMetric: HistoryMetric = HistoryMetric.CPU,
    history: List<ServerHistoryPointDto> = emptyList(),
    isHistoryLoading: Boolean = false,
    onMetricSelect: (HistoryMetric) -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ServerAction?>(null) }
    val hasActivePanel = uiState !is DashboardUiState.NoActivePanel
    val panel = when (uiState) {
        is DashboardUiState.Content -> uiState.panel
        is DashboardUiState.Error -> uiState.panel
        is DashboardUiState.Loading -> uiState.panel
        is DashboardUiState.NoActivePanel -> null
    }

    Scaffold(
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = panel?.name ?: stringResource(R.string.dashboard_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.dashboard_retry),
                            )
                        }
                        if (hasActivePanel) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                enabled = !isActionInFlight,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.dashboard_action_more_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dashboard_action_restart_xray)) },
                                    onClick = {
                                        menuExpanded = false
                                        pendingAction = ServerAction.Restart
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dashboard_action_stop_xray)) },
                                    onClick = {
                                        menuExpanded = false
                                        pendingAction = ServerAction.Stop
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dashboard_action_logs)) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateToLogs()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (panel != null) {
                    val subtitle = panel.baseUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (uiState) {
            is DashboardUiState.NoActivePanel -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.dashboard_no_panel_title),
                description = stringResource(R.string.dashboard_no_panel_description),
                actionLabel = stringResource(R.string.dashboard_add_panel),
                onAction = onAddPanel,
                modifier = Modifier.padding(padding),
            )

            is DashboardUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DashboardUiState.Error -> DashboardErrorState(
                error = uiState.error,
                onRetry = onRefresh,
                modifier = Modifier.padding(padding),
            )

            is DashboardUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                StatusList(
                    status = uiState.status,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToInbounds = onNavigateToInbounds,
                    selectedMetric = selectedMetric,
                    history = history,
                    isHistoryLoading = isHistoryLoading,
                    onMetricSelect = onMetricSelect,
                )
            }
        }
    }

    pendingAction?.let { action ->
        ServerActionConfirmDialog(
            action = action,
            onConfirm = {
                when (action) {
                    ServerAction.Restart -> onRestartXray()
                    ServerAction.Stop -> onStopXray()
                }
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
}

private enum class ServerAction { Restart, Stop }

@Composable
private fun ServerActionConfirmDialog(
    action: ServerAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = when (action) {
        ServerAction.Restart -> R.string.dashboard_restart_confirm_title
        ServerAction.Stop -> R.string.dashboard_stop_confirm_title
    }
    val messageRes = when (action) {
        ServerAction.Restart -> R.string.dashboard_restart_confirm_message
        ServerAction.Stop -> R.string.dashboard_stop_confirm_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dashboard_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_action_cancel))
            }
        },
    )
}

// ── Content list ───────────────────────────────────────────────────────────────

@Composable
private fun StatusList(
    status: ServerStatusDto,
    contentPadding: PaddingValues,
    onNavigateToStats: () -> Unit,
    onNavigateToInbounds: () -> Unit,
    selectedMetric: HistoryMetric,
    history: List<ServerHistoryPointDto>,
    isHistoryLoading: Boolean,
    onMetricSelect: (HistoryMetric) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item { HeroStatusCard(xray = status.xray, uptimeSeconds = status.uptime) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RingStat(
                    label = "CPU",
                    value = String.format("%.1f", status.cpu),
                    unit = "%",
                    progress = (status.cpu / 100.0).toFloat().coerceIn(0f, 1f),
                    sub = if (status.loads.isNotEmpty()) {
                        status.loads.joinToString(" · ") { "%.2f".format(it) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
                RingStat(
                    label = "Memory",
                    value = prettyBytes(status.mem.current),
                    unit = "",
                    progress = if (status.mem.total > 0) {
                        (status.mem.current.toFloat() / status.mem.total).coerceIn(0f, 1f)
                    } else 0f,
                    sub = "of ${prettyBytes(status.mem.total)}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            HistorySection(
                selectedMetric = selectedMetric,
                history = history,
                isLoading = isHistoryLoading,
                onMetricSelect = onMetricSelect,
            )
        }
        item { NetworkSection(status = status) }
        item { ServerSection(status = status) }
        item {
            MoreSection(
                onNavigateToStats = onNavigateToStats,
                onNavigateToInbounds = onNavigateToInbounds,
            )
        }
    }
}

// ── History section ────────────────────────────────────────────────────────────

@Composable
private fun HistorySection(
    selectedMetric: HistoryMetric,
    history: List<ServerHistoryPointDto>,
    isLoading: Boolean,
    onMetricSelect: (HistoryMetric) -> Unit,
) {
    SectionHeader(stringResource(R.string.dashboard_section_history))
    GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            SegmentedPicker(
                options = HistoryMetric.entries,
                selected = selectedMetric,
                onSelect = onMetricSelect,
                label = { metricLabel(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (isLoading && history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HistoryLineChart(points = history)
                Spacer(Modifier.height(6.dp))
                HistorySummary(metric = selectedMetric, points = history)
            }
        }
    }
}

@Composable
private fun metricLabel(metric: HistoryMetric): String = when (metric) {
    HistoryMetric.CPU -> stringResource(R.string.dashboard_history_metric_cpu)
    HistoryMetric.MEM -> stringResource(R.string.dashboard_history_metric_mem)
    HistoryMetric.NET_IN -> stringResource(R.string.dashboard_history_metric_net_in)
    HistoryMetric.NET_OUT -> stringResource(R.string.dashboard_history_metric_net_out)
    HistoryMetric.ONLINE -> stringResource(R.string.dashboard_history_metric_online)
}

@Composable
private fun HistorySummary(metric: HistoryMetric, points: List<ServerHistoryPointDto>) {
    if (points.size < 2) return
    val values = points.map { it.v }
    val min = values.min()
    val max = values.max()
    val avg = values.average()
    Text(
        text = stringResource(
            R.string.dashboard_history_summary,
            formatMetric(metric, min),
            formatMetric(metric, avg),
            formatMetric(metric, max),
        ),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatMetric(metric: HistoryMetric, value: Double): String = when (metric) {
    HistoryMetric.CPU -> "%.1f%%".format(value)
    HistoryMetric.MEM -> prettyBytes(value.toLong())
    HistoryMetric.NET_IN, HistoryMetric.NET_OUT -> "${prettyBytes(value.toLong())}/s"
    HistoryMetric.ONLINE -> "%.0f".format(value)
}

// ── Hero Card ──────────────────────────────────────────────────────────────────

@Composable
private fun HeroStatusCard(xray: XrayStatusDto, uptimeSeconds: Long) {
    val primary = MaterialTheme.colorScheme.primary
    val isRunning = xray.state.equals("running", ignoreCase = true)

    val gradientBrush = Brush.verticalGradient(
        listOf(
            primary.copy(alpha = 0.16f),
            primary.copy(alpha = 0.04f),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(brush = gradientBrush, shape = HeroCardShape)
            .border(
                width = 0.5.dp,
                color = primary.copy(alpha = 0.4f),
                shape = HeroCardShape,
            )
            .padding(18.dp),
    ) {
        Column {
            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(active = isRunning)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) {
                        stringResource(R.string.dashboard_hero_running)
                    } else {
                        stringResource(R.string.dashboard_hero_stopped)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                    ),
                    color = if (isRunning) primary else MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Bottom info row
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_hero_uptime),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = secondsToCompact(uptimeSeconds),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_hero_version),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "v${xray.version}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseDot(active: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val dotColor = if (active) primary else MaterialTheme.colorScheme.error

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center) {
        // Glow ring
        Box(
            modifier = Modifier
                .size(14.dp)
                .scale(scale)
                .background(color = dotColor.copy(alpha = 0.2f), shape = CircleShape),
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape),
        )
    }
}

// ── Network section ────────────────────────────────────────────────────────────

@Composable
private fun NetworkSection(status: ServerStatusDto) {
    SectionHeader(stringResource(R.string.dashboard_section_network))

    GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Live throughput
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_live_throughput),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val (upValue, upUnit) = formatSpeed(status.netIO.up)
                val (downValue, downUnit) = formatSpeed(status.netIO.down)
                SpeedColumn(
                    isUpload = true,
                    label = stringResource(R.string.dashboard_row_upload),
                    value = upValue,
                    unit = upUnit,
                    modifier = Modifier.weight(1f),
                )
                SpeedColumn(
                    isUpload = false,
                    label = stringResource(R.string.dashboard_row_download),
                    value = downValue,
                    unit = downUnit,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        GroupRow(
            label = stringResource(R.string.dashboard_row_sent_total),
            value = prettyBytes(status.netTraffic.sent),
            monoValue = true,
            showChevron = false,
            topDivider = true,
        )
        GroupRow(
            label = stringResource(R.string.dashboard_row_received_total),
            value = prettyBytes(status.netTraffic.recv),
            monoValue = true,
            showChevron = false,
            topDivider = true,
        )
    }
}

// ── Server section ─────────────────────────────────────────────────────────────

@Composable
private fun ServerSection(status: ServerStatusDto) {
    SectionHeader(stringResource(R.string.dashboard_section_server))

    GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (status.publicIP.ipv4.isNotBlank()) {
            GroupRow(
                label = stringResource(R.string.dashboard_row_ipv4),
                value = status.publicIP.ipv4,
                monoValue = true,
                showChevron = false,
            )
        }
        if (status.publicIP.ipv6.isNotBlank()) {
            GroupRow(
                label = stringResource(R.string.dashboard_row_ipv6),
                value = status.publicIP.ipv6.midEllipsis(),
                monoValue = true,
                showChevron = false,
                topDivider = status.publicIP.ipv4.isNotBlank(),
            )
        }
        GroupRow(
            label = stringResource(R.string.dashboard_row_tcp_udp),
            value = "${status.tcpCount} / ${status.udpCount}",
            monoValue = true,
            showChevron = false,
            topDivider = status.publicIP.ipv4.isNotBlank() || status.publicIP.ipv6.isNotBlank(),
        )
    }
}

// ── More section ───────────────────────────────────────────────────────────────

@Composable
private fun MoreSection(
    onNavigateToStats: () -> Unit,
    onNavigateToInbounds: () -> Unit,
) {
    SectionHeader(stringResource(R.string.dashboard_section_more))

    GroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        GroupRow(
            label = stringResource(R.string.dashboard_more_stats_label),
            sub = stringResource(R.string.dashboard_more_stats_sub),
            leadingIcon = Icons.AutoMirrored.Filled.ShowChart,
            showChevron = true,
            onClick = onNavigateToStats,
        )
        GroupRow(
            label = stringResource(R.string.dashboard_more_inbounds_label),
            leadingIcon = Icons.AutoMirrored.Filled.List,
            showChevron = true,
            topDivider = true,
            onClick = onNavigateToInbounds,
        )
    }
}

// ── Error state (with optional detail block) ───────────────────────────────────

@Composable
private fun DashboardErrorState(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = error.toDetailMessage()
    val description = buildString {
        append(error.toUserMessage())
        if (detail.isNotEmpty()) {
            append("\n\n")
            append(detail)
        }
    }
    ErrorState(
        title = stringResource(R.string.dashboard_error_title),
        description = description,
        actionLabel = stringResource(R.string.dashboard_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.dashboard_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.dashboard_error_tls, message)
    is DomainError.Network -> stringResource(R.string.dashboard_error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.dashboard_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.dashboard_error_response, body)
    is DomainError.Unexpected -> stringResource(R.string.dashboard_error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.dashboard_error_pin_mismatch)
}

private fun DomainError.toDetailMessage(): String = when (this) {
    is DomainError.Tls -> message
    is DomainError.PanelResponse -> body
    else -> ""
}

// ── Utils ──────────────────────────────────────────────────────────────────────

private const val MID_ELLIPSIS_MAX = 14

private fun String.midEllipsis(maxChars: Int = MID_ELLIPSIS_MAX): String {
    if (length <= maxChars) return this
    val half = maxChars / 2
    return "${take(half)}…${takeLast(half)}"
}

// ── Preview ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    XuiPanelTheme {
        DashboardContent(
            onNavigateToStats = {},
            uiState = DashboardUiState.Content(
                panel = Panel(
                    id = "1",
                    name = "Stockholm Edge",
                    baseUrl = "https://panel.northwind.io:2053",
                    login = "admin",
                    password = "pass",
                    tlsMode = TlsMode.SYSTEM,
                    pinnedSpkiSha256 = null,
                    pinnedAt = null,
                    isActive = true,
                    createdAt = Instant.now(),
                    lastLoginAt = Instant.now(),
                ),
                status = ServerStatusDto(
                    cpu = 23.4,
                    mem = MemDto(current = 1_500_000_000, total = 4_000_000_000),
                    xray = XrayStatusDto(state = "running", errorMsg = "", version = "1.8.24"),
                    uptime = 14 * 86_400L + 6 * 3_600L,
                    loads = listOf(0.45, 0.51, 0.48),
                    tcpCount = 12,
                    udpCount = 4,
                    netIO = NetIoDto(up = 12_621L, down = 69_530L),
                    netTraffic = NetTrafficDto(sent = 1_500_000_000, recv = 5_000_000_000),
                    publicIP = PublicIpDto(ipv4 = "203.0.113.10", ipv6 = "2a01:cafe:dead:beef::1f4b"),
                    appStats = null,
                ),
            ),
            isRefreshing = false,
            snackbarHostState = remember { SnackbarHostState() },
            isActionInFlight = false,
            onRefresh = {},
            onAddPanel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoActivePanelPreview() {
    XuiPanelTheme {
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "No active panel",
            description = "Add a panel to monitor its status and manage clients.",
            actionLabel = "Add panel",
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardErrorStatePreview() {
    XuiPanelTheme {
        DashboardErrorState(
            error = DomainError.Network(RuntimeException("Connection refused")),
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
