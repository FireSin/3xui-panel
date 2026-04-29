package com.firesin.xuipanel.feature.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.MemDto
import com.firesin.xuipanel.core.xui.dto.NetIoDto
import com.firesin.xuipanel.core.xui.dto.NetTrafficDto
import com.firesin.xuipanel.core.xui.dto.PublicIpDto
import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import com.firesin.xuipanel.core.xui.dto.XrayStatusDto
import com.firesin.xuipanel.core.designsystem.format.formatBytes
import com.firesin.xuipanel.feature.dashboard.ui.DashboardUiState
import com.firesin.xuipanel.feature.dashboard.ui.DashboardViewModel
import java.time.Instant

@Composable
fun DashboardScreen(
    onAddPanel: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onAddPanel = onAddPanel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAddPanel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val panel = (uiState as? DashboardUiState.Content)?.panel
                        ?: (uiState as? DashboardUiState.Error)?.panel
                        ?: (uiState as? DashboardUiState.Loading)?.panel
                    Column {
                        Text(
                            text = panel?.name ?: stringResource(R.string.dashboard_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (panel != null) {
                            Text(
                                text = panel.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            is DashboardUiState.NoActivePanel -> NoActivePanelEmpty(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddPanel = onAddPanel,
            )

            is DashboardUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DashboardUiState.Error -> ErrorState(
                error = uiState.error,
                onRetry = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusList(
    status: ServerStatusDto,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { XrayCard(status.xray) }
        item { CpuCard(cpu = status.cpu, loads = status.loads) }
        item { MemoryCard(mem = status.mem) }
        item { UptimeCard(uptimeSeconds = status.uptime) }
        item { NetworkSpeedCard(netIO = status.netIO) }
        item { TotalTrafficCard(traffic = status.netTraffic) }
        item { PublicIpCard(ip = status.publicIP) }
    }
}

@Composable
private fun XrayCard(xray: XrayStatusDto) {
    val isRunning = xray.state.equals("running", ignoreCase = true)
    val containerColor = if (isRunning) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_xray),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = xray.state.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_xray_version, xray.version),
                style = MaterialTheme.typography.bodySmall,
            )
            if (xray.errorMsg.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = xray.errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CpuCard(cpu: Double, loads: List<Double>) {
    StatCard(
        icon = { Icon(Icons.Default.Speed, contentDescription = null) },
        title = stringResource(R.string.dashboard_cpu),
        value = String.format(Locale.US, "%.1f%%", cpu),
    ) {
        LinearProgressIndicator(
            progress = { (cpu / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (loads.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.dashboard_load,
                    loads.getOrElse(0) { 0.0 },
                    loads.getOrElse(1) { 0.0 },
                    loads.getOrElse(2) { 0.0 },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoryCard(mem: MemDto) {
    val ratio = if (mem.total > 0) mem.current.toFloat() / mem.total else 0f
    StatCard(
        icon = { Icon(Icons.Default.Memory, contentDescription = null) },
        title = stringResource(R.string.dashboard_memory),
        value = stringResource(
            R.string.dashboard_memory_value,
            formatBytes(mem.current),
            formatBytes(mem.total),
        ),
    ) {
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UptimeCard(uptimeSeconds: Long) {
    StatCard(
        icon = null,
        title = stringResource(R.string.dashboard_uptime),
        value = formatUptime(uptimeSeconds),
        content = null,
    )
}

@Composable
private fun NetworkSpeedCard(netIO: NetIoDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_speed),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SpeedColumn(
                    icon = Icons.Default.ArrowUpward,
                    label = stringResource(R.string.dashboard_up),
                    value = "${formatBytes(netIO.up)}/s",
                    modifier = Modifier.weight(1f),
                )
                SpeedColumn(
                    icon = Icons.Default.ArrowDownward,
                    label = stringResource(R.string.dashboard_down),
                    value = "${formatBytes(netIO.down)}/s",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TotalTrafficCard(traffic: NetTrafficDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_total_traffic),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SpeedColumn(
                    icon = Icons.Default.ArrowUpward,
                    label = stringResource(R.string.dashboard_sent),
                    value = formatBytes(traffic.sent),
                    modifier = Modifier.weight(1f),
                )
                SpeedColumn(
                    icon = Icons.Default.ArrowDownward,
                    label = stringResource(R.string.dashboard_received),
                    value = formatBytes(traffic.recv),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PublicIpCard(ip: PublicIpDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_public_ip),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            if (ip.ipv4.isNotBlank()) {
                Text(text = "IPv4: ${ip.ipv4}", style = MaterialTheme.typography.bodyMedium)
            }
            if (ip.ipv6.isNotBlank()) {
                Text(text = "IPv6: ${ip.ipv6}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: (@Composable () -> Unit)?,
    content: (@Composable () -> Unit)? = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    Text(text = title, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge)
            content?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
private fun SpeedColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NoActivePanelEmpty(
    modifier: Modifier = Modifier,
    onAddPanel: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.dashboard_no_active_panel),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPanel) {
            Text(stringResource(R.string.dashboard_add_panel))
        }
    }
}

@Composable
private fun ErrorState(
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
            Text(stringResource(R.string.dashboard_retry))
        }
    }
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

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    XuiPanelTheme {
        DashboardContent(
            uiState = DashboardUiState.Content(
                panel = Panel(
                    id = "1",
                    name = "Мой сервер",
                    baseUrl = "https://panel.example.com:2053",
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
                    uptime = 123_456,
                    loads = listOf(0.45, 0.51, 0.48),
                    tcpCount = 12,
                    udpCount = 4,
                    netIO = NetIoDto(up = 12_345, down = 67_890),
                    netTraffic = NetTrafficDto(sent = 1_500_000_000, recv = 5_000_000_000),
                    publicIP = PublicIpDto(ipv4 = "203.0.113.10", ipv6 = "::1"),
                    appStats = null,
                ),
            ),
            isRefreshing = false,
            onRefresh = {},
            onAddPanel = {},
        )
    }
}
