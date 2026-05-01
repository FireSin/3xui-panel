package com.firesin.xuipanel.feature.clients

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.firesin.xuipanel.core.designsystem.format.formatBytes
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.urlKey
import com.firesin.xuipanel.feature.clients.ui.ClientsUiState
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel
import com.firesin.xuipanel.feature.clients.ui.isSupportedProtocol
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun ClientsListScreen(
    initialInboundId: Int? = null,
    onAddPanel: () -> Unit = {},
    onNavigateAdd: (inboundId: Int) -> Unit = {},
    onNavigateEdit: (inboundId: Int, clientKey: String) -> Unit = { _, _ -> },
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Apply initial inbound selection once content is available
    LaunchedEffect(uiState, initialInboundId) {
        if (initialInboundId != null && uiState is ClientsUiState.Content) {
            val content = uiState as ClientsUiState.Content
            if (content.selectedInboundId != initialInboundId &&
                content.inbounds.any { it.id == initialInboundId }
            ) {
                viewModel.selectInbound(initialInboundId)
            }
        }
    }

    var pendingDelete by remember { mutableStateOf<ClientConfig?>(null) }
    var pendingReset by remember { mutableStateOf<ClientConfig?>(null) }

    val resolvedError = errorMessage?.toUserMessage()
    LaunchedEffect(resolvedError) {
        resolvedError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    ClientsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onAddPanel = onAddPanel,
        onSelectInbound = viewModel::selectInbound,
        onNavigateAdd = onNavigateAdd,
        onNavigateEdit = { inboundId, key -> onNavigateEdit(inboundId, key) },
        onDeleteRequest = { client -> pendingDelete = client },
        onResetRequest = { client -> pendingReset = client },
    )

    pendingDelete?.let { client ->
        val inboundId = (uiState as? ClientsUiState.Content)?.selectedInboundId
        ClientDeleteConfirmDialog(
            clientEmail = client.email,
            onConfirm = {
                if (inboundId != null) viewModel.deleteClient(inboundId, client)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingReset?.let { client ->
        val inboundId = (uiState as? ClientsUiState.Content)?.selectedInboundId
        ClientResetConfirmDialog(
            clientEmail = client.email,
            onConfirm = {
                if (inboundId != null) viewModel.resetTraffic(inboundId, client)
                pendingReset = null
            },
            onDismiss = { pendingReset = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientsContent(
    uiState: ClientsUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAddPanel: () -> Unit,
    onSelectInbound: (Int) -> Unit,
    onNavigateAdd: (inboundId: Int) -> Unit,
    onNavigateEdit: (inboundId: Int, clientKey: String) -> Unit,
    onDeleteRequest: (ClientConfig) -> Unit,
    onResetRequest: (ClientConfig) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val panel = (uiState as? ClientsUiState.Content)?.panel
                        ?: (uiState as? ClientsUiState.Error)?.panel
                        ?: (uiState as? ClientsUiState.Loading)?.panel
                    Column {
                        Text(
                            text = panel?.name ?: stringResource(R.string.clients_title),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState is ClientsUiState.Content) {
                val selectedId = uiState.selectedInboundId
                val selectedInbound = uiState.inbounds.firstOrNull { it.id == selectedId }
                val isSupported = selectedInbound?.protocol?.isSupportedProtocol() ?: false
                if (isSupported && selectedId != null) {
                    FloatingActionButton(onClick = { onNavigateAdd(selectedId) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.clients_fab_add),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (uiState) {
            is ClientsUiState.NoActivePanel -> NoActivePanelEmpty(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddPanel = onAddPanel,
            )

            is ClientsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is ClientsUiState.Error -> ErrorState(
                error = uiState.error,
                onRetry = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is ClientsUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.inbounds.isNotEmpty()) {
                        InboundDropdown(
                            inbounds = uiState.inbounds,
                            selectedId = uiState.selectedInboundId,
                            onSelect = onSelectInbound,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    val selectedInbound = uiState.inbounds.firstOrNull { it.id == uiState.selectedInboundId }
                    val isSupported = selectedInbound?.protocol?.isSupportedProtocol() ?: false

                    if (selectedInbound != null && !isSupported) {
                        ReadOnlyBanner(
                            protocol = selectedInbound.protocol,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    if (uiState.clients.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.clients_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.clients, key = { it.email }) { client ->
                                ClientCard(
                                    client = client,
                                    inboundId = uiState.selectedInboundId ?: 0,
                                    isSupported = isSupported,
                                    onEdit = { inboundId, key -> onNavigateEdit(inboundId, key) },
                                    onReset = { onResetRequest(client) },
                                    onDelete = { onDeleteRequest(client) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboundDropdown(
    inbounds: List<InboundDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedInbound = inbounds.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedInbound?.displayName() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.clients_inbound_dropdown_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            inbounds.forEach { inbound ->
                DropdownMenuItem(
                    text = { Text(inbound.displayName()) },
                    onClick = {
                        onSelect(inbound.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyBanner(
    protocol: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.clients_readonly_banner, protocol),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ClientCard(
    client: ClientConfig,
    inboundId: Int,
    isSupported: Boolean,
    onEdit: (inboundId: Int, clientKey: String) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = client.email.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Badge(
                        containerColor = if (client.enable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            text = if (client.enable) {
                                stringResource(R.string.clients_enabled)
                            } else {
                                stringResource(R.string.clients_disabled)
                            },
                            color = if (client.enable) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = trafficLabel(client.totalGB),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = expiryLabel(client.expiryTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (client.limitIp > 0) {
                    Text(
                        text = stringResource(R.string.clients_limit_ip, client.limitIp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isSupported) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.clients_cd_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clients_menu_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit(inboundId, client.urlKey)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clients_menu_reset_traffic)) },
                            onClick = {
                                menuExpanded = false
                                onReset()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clients_menu_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun trafficLabel(totalGB: Long): String =
    if (totalGB == 0L) {
        stringResource(R.string.clients_traffic_unlimited)
    } else {
        stringResource(R.string.clients_traffic, formatBytes(totalGB))
    }

@Composable
private fun expiryLabel(expiryTime: Long): String =
    if (expiryTime == 0L) {
        stringResource(R.string.clients_no_expiry)
    } else {
        stringResource(
            R.string.clients_expiry,
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expiryTime)),
        )
    }

private fun InboundDto.displayName(): String =
    remark.ifBlank { "#${id} ${protocol}:${port}" }

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
            text = stringResource(R.string.clients_no_active_panel),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPanel) {
            Text(stringResource(R.string.clients_add_panel))
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
            Text(stringResource(R.string.clients_retry))
        }
    }
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.clients_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.clients_error_tls, message)
    is DomainError.Network -> stringResource(R.string.clients_error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.clients_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.clients_error_response, body)
    is DomainError.Unexpected -> stringResource(R.string.clients_error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.clients_error_pin_mismatch)
}

@Preview(showBackground = true)
@Composable
private fun ClientsContentPreview() {
    XuiPanelTheme {
        val panel = Panel(
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
            lastLoginAt = null,
        )
        ClientsContent(
            uiState = ClientsUiState.Content(
                panel = panel,
                inbounds = listOf(
                    InboundDto(
                        id = 1, remark = "VMess-443", port = 443, protocol = "vmess",
                        enable = true, up = 0L, down = 0L, total = 0L, expiryTime = 0L,
                        listen = "", settings = "{\"clients\":[]}", streamSettings = "{}",
                        tag = "inbound-443", sniffing = "{}",
                    ),
                ),
                selectedInboundId = 1,
                clients = listOf(
                    ClientConfig.Vmess(
                        id = "11111111-2222-3333-4444-555555555555",
                        email = "user@example.com",
                        enable = true,
                        totalGB = 10L * 1_073_741_824L,
                        expiryTime = 0L,
                        limitIp = 2,
                        subId = "sub123",
                        comment = "",
                    ),
                    ClientConfig.Vmess(
                        id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        email = "disabled@test.com",
                        enable = false,
                        totalGB = 0L,
                        expiryTime = 1780000000000L,
                        limitIp = 0,
                        subId = "",
                        comment = "test",
                    ),
                ),
            ),
            isRefreshing = false,
            snackbarHostState = SnackbarHostState(),
            onRefresh = {},
            onAddPanel = {},
            onSelectInbound = {},
            onNavigateAdd = {},
            onNavigateEdit = { _, _ -> },
            onDeleteRequest = {},
            onResetRequest = {},
        )
    }
}
