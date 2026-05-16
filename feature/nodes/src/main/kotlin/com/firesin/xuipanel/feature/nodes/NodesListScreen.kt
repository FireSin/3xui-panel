package com.firesin.xuipanel.feature.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.util.secondsToCompact
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.component.EmptyState
import com.firesin.xuipanel.core.designsystem.component.ErrorState
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.PanelChip
import com.firesin.xuipanel.core.designsystem.component.PanelStatus
import com.firesin.xuipanel.core.designsystem.component.PanelSwitcherEntry
import com.firesin.xuipanel.core.designsystem.component.PanelSwitcherSheet
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.NodeDto
import com.firesin.xuipanel.feature.nodes.ui.NodesListViewModel
import com.firesin.xuipanel.feature.nodes.ui.NodesUiState

private val CardShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(5.dp)

@Composable
fun NodesListScreen(
    onMenuClick: () -> Unit = {},
    onAddNode: () -> Unit = {},
    onEditNode: (nodeId: Int) -> Unit = {},
    onViewDetail: (nodeId: Int) -> Unit = {},
    viewModel: NodesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val allPanels by viewModel.allPanels.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }
    var showSwitcher by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val resolvedError = errorMessage?.toUserMessage()
    LaunchedEffect(resolvedError) {
        resolvedError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    NodesContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onMenuClick = onMenuClick,
        onAddNode = onAddNode,
        onEditNode = onEditNode,
        onViewDetail = onViewDetail,
        onSetEnable = viewModel::setEnable,
        onProbe = viewModel::probe,
        onDeleteNode = { id, name ->
            pendingDeleteId = id
            pendingDeleteName = name
        },
        onPanelChipClick = { showSwitcher = true },
    )

    pendingDeleteId?.let { id ->
        NodeDeleteConfirmDialog(
            nodeName = pendingDeleteName,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }

    if (showSwitcher) {
        val activeId = when (val s = uiState) {
            is NodesUiState.Content -> s.panel.id
            is NodesUiState.Loading -> s.panel.id
            is NodesUiState.Error -> s.panel.id
            NodesUiState.NoActivePanel -> null
        }
        PanelSwitcherSheet(
            entries = allPanels.map { it.toSwitcherEntry(activeId = activeId) },
            onSelect = { id ->
                viewModel.setActivePanel(id)
                showSwitcher = false
            },
            onAddPanel = { showSwitcher = false },
            onDismiss = { showSwitcher = false },
        )
    }
}

private fun Panel.toSwitcherEntry(activeId: String?): PanelSwitcherEntry =
    PanelSwitcherEntry(
        id = id,
        name = name,
        host = baseUrl.removePrefix("https://").removePrefix("http://"),
        status = PanelStatus.Up,
        active = id == activeId,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodesContent(
    uiState: NodesUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit = {},
    onAddNode: () -> Unit = {},
    onEditNode: (nodeId: Int) -> Unit = {},
    onViewDetail: (nodeId: Int) -> Unit = {},
    onSetEnable: (nodeId: Int, enable: Boolean) -> Unit = { _, _ -> },
    onProbe: (nodeId: Int) -> Unit = {},
    onDeleteNode: (nodeId: Int, name: String) -> Unit = { _, _ -> },
    onPanelChipClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val panel = when (uiState) {
        is NodesUiState.Content -> uiState.panel
        is NodesUiState.Loading -> uiState.panel
        is NodesUiState.Error -> uiState.panel
        NodesUiState.NoActivePanel -> null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
            TopAppBar(
                title = { Text(stringResource(R.string.nodes_title)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.nodes_action_refresh),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
            if (panel != null) {
                PanelChip(
                    name = panel.name,
                    host = panel.baseUrl.removePrefix("https://").removePrefix("http://"),
                    status = PanelStatus.Up,
                    onClick = onPanelChipClick,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            }
        },
        floatingActionButton = {
            if (uiState is NodesUiState.Content || uiState is NodesUiState.Error) {
                FloatingActionButton(onClick = onAddNode) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.nodes_cd_add),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (uiState) {
            is NodesUiState.NoActivePanel -> EmptyState(
                icon = Icons.Default.Cloud,
                title = stringResource(R.string.nodes_no_active_panel_title),
                description = stringResource(R.string.nodes_no_active_panel_description),
                modifier = Modifier.padding(padding),
            )

            is NodesUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is NodesUiState.Error -> ErrorState(
                title = stringResource(R.string.nodes_error_title),
                description = uiState.error.toUserMessage(),
                actionLabel = stringResource(R.string.nodes_retry),
                onAction = onRefresh,
                modifier = Modifier.padding(padding),
            )

            is NodesUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (uiState.nodes.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Cloud,
                        title = stringResource(R.string.nodes_empty_title),
                        description = stringResource(R.string.nodes_empty_description),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.nodes, key = { it.id }) { node ->
                            NodeCard(
                                node = node,
                                onToggleEnabled = { onSetEnable(node.id, it) },
                                onProbe = { onProbe(node.id) },
                                onEdit = { onEditNode(node.id) },
                                onDelete = { onDeleteNode(node.id, node.displayName()) },
                                onClick = { onViewDetail(node.id) },
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: NodeDto,
    onToggleEnabled: (Boolean) -> Unit = {},
    onProbe: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: name + status pill + spacer + toggle + overflow menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = node.displayName(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                NodeStatusPill(status = node.status)
                IosToggle(
                    checked = node.enable,
                    onCheckedChange = onToggleEnabled,
                )
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.nodes_cd_more),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nodes_action_probe)) },
                            onClick = {
                                overflowExpanded = false
                                onProbe()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nodes_action_edit)) },
                            onClick = {
                                overflowExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nodes_action_delete)) },
                            onClick = {
                                overflowExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            // Subtitle: scheme://address:port (monospace)
            Text(
                text = "${node.scheme}://${node.address}:${node.port}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            val isError = node.status.equals("error", ignoreCase = true) ||
                (node.lastError.isNotBlank() && !node.status.equals("online", ignoreCase = true))

            if (isError) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = node.lastError.ifBlank { stringResource(R.string.nodes_unreachable) },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NodeStatCell(
                        caption = stringResource(R.string.nodes_stat_label_latency),
                        value = "${node.latencyMs} ${stringResource(R.string.nodes_stat_unit_ms)}",
                        modifier = Modifier.weight(1f),
                    )
                    NodeStatCell(
                        caption = stringResource(R.string.nodes_stat_label_cpu),
                        value = "%.1f%%".format(node.cpuPct),
                        progress = (node.cpuPct / 100f).toFloat(),
                        warn = node.cpuPct > 60.0,
                        modifier = Modifier.weight(1f),
                    )
                    NodeStatCell(
                        caption = stringResource(R.string.nodes_stat_label_mem),
                        value = "%.0f%%".format(node.memPct),
                        progress = (node.memPct / 100f).toFloat(),
                        warn = node.memPct > 60.0,
                        modifier = Modifier.weight(1f),
                    )
                    NodeStatCell(
                        caption = stringResource(R.string.nodes_stat_label_xray),
                        value = if (node.xrayVersion.isNotBlank()) "v${node.xrayVersion}" else "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (node.uptimeSecs > 0L) {
                    Text(
                        text = stringResource(R.string.nodes_stat_uptime, secondsToCompact(node.uptimeSecs)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeStatCell(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    warn: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(
                            color = if (warn) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun NodeStatusPill(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (bg, textColor, label) = when (status.lowercase()) {
        "online" -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.nodes_status_online),
        )
        "error" -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.error,
            stringResource(R.string.nodes_status_error),
        )
        "offline" -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.nodes_status_offline),
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.nodes_status_unknown),
        )
    }
    Box(
        modifier = modifier
            .background(color = bg, shape = PillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
            color = textColor,
        )
    }
}

@Composable
private fun NodeDeleteConfirmDialog(
    nodeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nodes_delete_title)) },
        text = { Text(stringResource(R.string.nodes_delete_message, nodeName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.nodes_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nodes_form_cancel))
            }
        },
    )
}

private fun NodeDto.displayName(): String = name.ifBlank { "node-$id" }

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.nodes_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.nodes_error_tls, message)
    is DomainError.Network -> {
        val base = stringResource(R.string.nodes_error_network)
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        "$base ($detail)"
    }
    is DomainError.PanelUnreachable -> stringResource(R.string.nodes_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.nodes_error_response, body)
    is DomainError.Unexpected -> {
        val base = stringResource(R.string.nodes_error_unexpected)
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        "$base ($detail)"
    }
    is DomainError.PinMismatch -> stringResource(R.string.nodes_error_pin_mismatch)
    is DomainError.NotFound -> message
}

@Preview(showBackground = true)
@Composable
private fun NodeCardPreview() {
    XuiPanelTheme {
        NodeCard(
            node = NodeDto(
                id = 1,
                name = "Frankfurt Edge",
                remark = "backup",
                scheme = "https",
                address = "fra.example.com",
                port = 2053,
                status = "online",
                latencyMs = 42,
                cpuPct = 12.5,
                memPct = 38.0,
                uptimeSecs = 86400L + 3600L,
                xrayVersion = "25.4.0",
            ),
        )
    }
}
