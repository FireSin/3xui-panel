package com.firesin.xuipanel.feature.inbounds

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import com.firesin.xuipanel.core.designsystem.component.EmptyState
import com.firesin.xuipanel.core.designsystem.component.ErrorState
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.inbounds.ui.InboundsUiState
import com.firesin.xuipanel.feature.inbounds.ui.InboundsViewModel
import java.time.Instant

private val CardShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(5.dp)

@Composable
fun InboundsListScreen(
    onAddPanel: () -> Unit = {},
    onManageClients: (inboundId: Int) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onNavigateAddInbound: () -> Unit = {},
    onNavigateEditInbound: (inboundId: Int) -> Unit = {},
    viewModel: InboundsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }

    // copyClients dialog: target inbound id and full inbounds list for source selection
    var copyClientsTargetId by remember { mutableStateOf<Int?>(null) }

    // importInbounds dialog
    var showImportDialog by remember { mutableStateOf(false) }

    // snackbar messages for success operations
    val copySuccessMsg = stringResource(R.string.inbounds_copy_clients_success)
    val importSuccessMsg = stringResource(R.string.inbounds_import_success)
    var pendingSuccessMsg by remember { mutableStateOf<String?>(null) }

    // Refresh inbounds when the screen comes back into focus (add/delete via child screens).
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

    LaunchedEffect(pendingSuccessMsg) {
        pendingSuccessMsg?.let {
            snackbarHostState.showSnackbar(it)
            pendingSuccessMsg = null
        }
    }

    val allInbounds = (uiState as? InboundsUiState.Content)?.inbounds.orEmpty()

    InboundsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onAddPanel = onAddPanel,
        onManageClients = onManageClients,
        onToggleEnabled = viewModel::toggle,
        onMenuClick = onMenuClick,
        onAddInboundClick = onNavigateAddInbound,
        onEditInbound = onNavigateEditInbound,
        onDeleteInbound = { id, name ->
            pendingDeleteId = id
            pendingDeleteName = name
        },
        onCopyClientsClick = { targetId -> copyClientsTargetId = targetId },
        onImportClick = { showImportDialog = true },
    )

    pendingDeleteId?.let { id ->
        InboundDeleteConfirmDialog(
            inboundName = pendingDeleteName,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }

    copyClientsTargetId?.let { targetId ->
        val otherInbounds = allInbounds.filter { it.id != targetId && it.protocol.protocolHasClients() }
        CopyClientsDialog(
            otherInbounds = otherInbounds,
            onConfirm = { sourceId, emails, flow ->
                viewModel.copyClients(
                    targetInboundId = targetId,
                    sourceInboundId = sourceId,
                    clientEmails = emails,
                    flow = flow,
                )
                copyClientsTargetId = null
                pendingSuccessMsg = copySuccessMsg
            },
            onDismiss = { copyClientsTargetId = null },
        )
    }

    if (showImportDialog) {
        ImportInboundsDialog(
            onConfirm = { jsonText ->
                viewModel.importInbounds(jsonText)
                showImportDialog = false
                pendingSuccessMsg = importSuccessMsg
            },
            onDismiss = { showImportDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboundsContent(
    uiState: InboundsUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAddPanel: () -> Unit,
    onManageClients: (inboundId: Int) -> Unit = {},
    onToggleEnabled: (inboundId: Int, enable: Boolean) -> Unit = { _, _ -> },
    onMenuClick: () -> Unit = {},
    onAddInboundClick: () -> Unit = {},
    onEditInbound: (inboundId: Int) -> Unit = {},
    onDeleteInbound: (inboundId: Int, name: String) -> Unit = { _, _ -> },
    onCopyClientsClick: (targetInboundId: Int) -> Unit = {},
    onImportClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val panel = (uiState as? InboundsUiState.Content)?.panel
        ?: (uiState as? InboundsUiState.Error)?.panel
        ?: (uiState as? InboundsUiState.Loading)?.panel

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.inbounds_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.inbounds_cd_menu_open),
                        )
                    }
                },
                actions = {
                    var topBarMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { topBarMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.inbounds_cd_more),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        DropdownMenu(
                            expanded = topBarMenuExpanded,
                            onDismissRequest = { topBarMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.inbounds_action_import_json)) },
                                onClick = {
                                    topBarMenuExpanded = false
                                    onImportClick()
                                },
                            )
                        }
                    }
                    IconButton(onClick = onAddInboundClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.inbounds_cd_add),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (uiState) {
            is InboundsUiState.NoActivePanel -> EmptyState(
                icon = Icons.Default.Dns,
                title = stringResource(R.string.inbounds_no_active_panel_title),
                description = stringResource(R.string.inbounds_no_active_panel_description),
                actionLabel = stringResource(R.string.inbounds_add_panel),
                onAction = onAddPanel,
                modifier = Modifier.padding(padding),
            )

            is InboundsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is InboundsUiState.Error -> ErrorState(
                title = stringResource(R.string.inbounds_error_title),
                description = uiState.error.toUserMessage(),
                actionLabel = stringResource(R.string.inbounds_retry),
                onAction = onRefresh,
                modifier = Modifier.padding(padding),
            )

            is InboundsUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Subtitle: "PanelName · N inbounds"
                    if (panel != null) {
                        Text(
                            text = stringResource(
                                R.string.inbounds_subtitle,
                                panel.name,
                                uiState.inbounds.size,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                        )
                    }

                    if (uiState.inbounds.isEmpty()) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.List,
                            title = stringResource(R.string.inbounds_empty_title),
                            description = stringResource(R.string.inbounds_empty_description),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(uiState.inbounds, key = { it.id }) { inbound ->
                                InboundCard(
                                    inbound = inbound,
                                    onManageClients = { onManageClients(inbound.id) },
                                    onToggleEnabled = { newValue ->
                                        onToggleEnabled(inbound.id, newValue)
                                    },
                                    onEditInbound = { onEditInbound(inbound.id) },
                                    onDeleteInbound = {
                                        onDeleteInbound(inbound.id, inbound.displayName())
                                    },
                                    onCopyClients = { onCopyClientsClick(inbound.id) },
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboundCard(
    inbound: InboundDto,
    onManageClients: () -> Unit = {},
    onToggleEnabled: (Boolean) -> Unit = {},
    onEditInbound: () -> Unit = {},
    onDeleteInbound: () -> Unit = {},
    onCopyClients: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isReadOnly = !inbound.protocol.isEditableProtocol()
    val hasClients = inbound.protocol.protocolHasClients()
    val canEdit = true
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hasClients) Modifier.clickable(onClick = onManageClients) else Modifier),
        shape = CardShape,
        color = if (hasClients) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: pill + port + spacer + toggle (for editable protocols) + overflow menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusPill(enabled = inbound.enable, readOnly = isReadOnly)
                Text(
                    text = ":${inbound.port}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (!isReadOnly) {
                    IosToggle(
                        checked = inbound.enable,
                        onCheckedChange = onToggleEnabled,
                    )
                }
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.inbounds_cd_more),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.inbounds_action_edit)) },
                                onClick = {
                                    overflowExpanded = false
                                    onEditInbound()
                                },
                            )
                        }
                        if (inbound.protocol.protocolHasClients()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.inbounds_action_copy_clients)) },
                                onClick = {
                                    overflowExpanded = false
                                    onCopyClients()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.inbounds_action_delete)) },
                            onClick = {
                                overflowExpanded = false
                                onDeleteInbound()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Name
            Text(
                text = inbound.displayName(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Proto
            Text(
                text = inbound.protocol.uppercase(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            // Hairline divider
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Stats row — hide the clients count for protocols that don't support clients
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (hasClients) {
                    StatColumn(
                        caption = stringResource(R.string.inbounds_stat_clients),
                        value = (inbound.clientStats?.size ?: 0).toString(),
                    )
                }
                StatColumn(
                    caption = stringResource(R.string.inbounds_stat_traffic),
                    value = inbound.trafficLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    enabled: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val (bg, textColor, label) = when {
        readOnly -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.inbounds_pill_readonly),
        )
        enabled -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.inbounds_pill_on),
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.inbounds_pill_off),
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
private fun StatColumn(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}


/**
 * Dialog for copying clients from one inbound into another.
 *
 * [otherInbounds] — список инбаундов-источников (уже отфильтрован: без текущего, только с клиентами).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyClientsDialog(
    otherInbounds: List<InboundDto>,
    onConfirm: (sourceId: Int, clientEmails: List<String>, flow: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (otherInbounds.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.inbounds_copy_clients_title)) },
            text = { Text(stringResource(R.string.inbounds_copy_clients_no_source)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.inbounds_copy_clients_cancel))
                }
            },
        )
        return
    }

    var sourceExpanded by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf(otherInbounds.first()) }
    val clientChecks = remember(selectedSource) {
        mutableStateMapOf<String, Boolean>().also { map ->
            selectedSource.clientStats?.forEach { stat -> map[stat.email] = false }
        }
    }
    var flowText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbounds_copy_clients_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Source inbound dropdown
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedSource.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.inbounds_copy_clients_source_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false },
                    ) {
                        otherInbounds.forEach { ib ->
                            DropdownMenuItem(
                                text = { Text(ib.displayName()) },
                                onClick = {
                                    selectedSource = ib
                                    sourceExpanded = false
                                },
                            )
                        }
                    }
                }

                // Client selection checkboxes (if source has clientStats)
                val clients = selectedSource.clientStats
                if (!clients.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.inbounds_copy_clients_select_clients),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    clients.forEach { stat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = clientChecks[stat.email] == true,
                                onCheckedChange = { checked -> clientChecks[stat.email] = checked },
                            )
                            Text(
                                text = stat.email,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                // Flow override
                OutlinedTextField(
                    value = flowText,
                    onValueChange = { flowText = it },
                    label = { Text(stringResource(R.string.inbounds_copy_clients_flow_label)) },
                    placeholder = { Text(stringResource(R.string.inbounds_copy_clients_flow_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedEmails = clientChecks.entries
                        .filter { it.value }
                        .map { it.key }
                    onConfirm(
                        selectedSource.id,
                        selectedEmails, // empty = all
                        flowText.takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text(stringResource(R.string.inbounds_copy_clients_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.inbounds_copy_clients_cancel))
            }
        },
    )
}

/**
 * Dialog for bulk-importing inbounds via raw JSON text.
 */
@Composable
private fun ImportInboundsDialog(
    onConfirm: (jsonText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbounds_import_title)) },
        text = {
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text(stringResource(R.string.inbounds_import_json_label)) },
                placeholder = { Text(stringResource(R.string.inbounds_import_json_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 20,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(jsonText) },
                enabled = jsonText.isNotBlank(),
            ) {
                Text(stringResource(R.string.inbounds_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.inbounds_import_cancel))
            }
        },
    )
}

private fun InboundDto.displayName(): String =
    remark.ifBlank { "${protocol}-${port}" }

private fun InboundDto.trafficLabel(): String {
    val used = prettyBytes(up + down)
    val limit = if (total > 0L) prettyBytes(total) else "∞"
    return "$used / $limit"
}

private fun String.isEditableProtocol(): Boolean =
    lowercase() in setOf("vmess", "vless", "shadowsocks")

/**
 * Protocols whose `settings.clients` is a multi-user list — those are the only inbounds where
 * a "Manage clients" drill-down makes sense. Mixed/HTTP/WireGuard/Tunnel/TUN have either
 * peer/account lists or no users at all, so we hide the navigation for them.
 */
private fun String.protocolHasClients(): Boolean =
    lowercase() in setOf("vmess", "vless", "trojan", "shadowsocks", "hysteria", "hysteria2")

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.inbounds_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.inbounds_error_tls, message)
    is DomainError.Network -> {
        // Include underlying cause for diagnosis (e.g. SocketTimeoutException, EOFException).
        val base = stringResource(R.string.inbounds_error_network)
        val detail = cause.message?.takeIf { it.isNotBlank() }
            ?: cause::class.java.simpleName
        "$base ($detail)"
    }
    is DomainError.PanelUnreachable -> stringResource(R.string.inbounds_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.inbounds_error_response, body)
    is DomainError.Unexpected -> {
        val base = stringResource(R.string.inbounds_error_unexpected)
        val detail = cause.message?.takeIf { it.isNotBlank() }
            ?: cause::class.java.simpleName
        "$base ($detail)"
    }
    is DomainError.PinMismatch -> stringResource(R.string.inbounds_error_pin_mismatch)
}

@Preview(showBackground = true)
@Composable
private fun InboundsContentPreview() {
    XuiPanelTheme {
        val panel = Panel(
            id = "1",
            name = "Stockholm Edge",
            baseUrl = "https://panel.example.com:2053",
            login = "admin",
            password = "pass",
            tlsMode = TlsMode.SYSTEM,
            pinnedSpkiSha256 = null,
            pinnedAt = null,
            isActive = true,
            createdAt = Instant.now(),
            lastLoginAt = null,
        )
        InboundsContent(
            uiState = InboundsUiState.Content(
                panel = panel,
                inbounds = listOf(
                    InboundDto(
                        id = 1,
                        remark = "vless-reality-443",
                        port = 443,
                        protocol = "vless",
                        enable = true,
                        up = 1_500_000_000L,
                        down = 5_000_000_000L,
                        total = 0L,
                        expiryTime = 0L,
                        listen = "",
                        settings = "{}",
                        streamSettings = "{}",
                        tag = "inbound-443",
                        sniffing = "{}",
                    ),
                    InboundDto(
                        id = 2,
                        remark = "vmess-ws-2096",
                        port = 2096,
                        protocol = "vmess",
                        enable = false,
                        up = 120_000_000L,
                        down = 0L,
                        total = 0L,
                        expiryTime = 0L,
                        listen = "",
                        settings = "{}",
                        streamSettings = "{}",
                        tag = "inbound-2096",
                        sniffing = "{}",
                    ),
                ),
            ),
            isRefreshing = false,
            snackbarHostState = SnackbarHostState(),
            onRefresh = {},
            onAddPanel = {},
            onManageClients = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InboundsNoActivePanelPreview() {
    XuiPanelTheme {
        InboundsContent(
            uiState = InboundsUiState.NoActivePanel,
            isRefreshing = false,
            snackbarHostState = SnackbarHostState(),
            onRefresh = {},
            onAddPanel = {},
            onManageClients = {},
        )
    }
}
