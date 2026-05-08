package com.firesin.xuipanel.feature.clients

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.common.util.ExpiryLabel
import com.firesin.xuipanel.core.common.util.classifyExpiry
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.core.xui.dto.urlKey
import com.firesin.xuipanel.feature.clients.ui.ClientsUiState
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel
import com.firesin.xuipanel.feature.clients.ui.isSupportedProtocol
import java.time.Instant


private val SearchBarShape = RoundedCornerShape(10.dp)
private val GroupCardShape = RoundedCornerShape(14.dp)

private const val WARN_THRESHOLD_DAYS = 7
private const val HIGH_USAGE_THRESHOLD = 0.9f

@Composable
fun ClientsListScreen(
    initialInboundId: Int? = null,
    onAddPanel: () -> Unit = {},
    onNavigateAdd: (inboundId: Int) -> Unit = {},
    onNavigateEdit: (inboundId: Int, clientKey: String) -> Unit = { _, _ -> },
    onPopBackStack: () -> Unit = {},
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

    var pendingDeleteInbound by remember { mutableStateOf(false) }

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
        onNavigateAdd = onNavigateAdd,
        onNavigateEdit = { inboundId, key -> onNavigateEdit(inboundId, key) },
        onDeleteInboundRequest = { pendingDeleteInbound = true },
        onPopBackStack = onPopBackStack,
    )

    if (pendingDeleteInbound) {
        val content = uiState as? ClientsUiState.Content
        val inboundName = content?.selectedInbound()?.let { inboundDisplayName(it) } ?: ""
        InboundDeleteConfirmDialogInline(
            inboundName = inboundName,
            onConfirm = {
                val id = (uiState as? ClientsUiState.Content)?.selectedInboundId
                if (id != null) {
                    viewModel.deleteInbound(id)
                }
                pendingDeleteInbound = false
                onPopBackStack()
            },
            onDismiss = { pendingDeleteInbound = false },
        )
    }
}

private fun ClientsUiState.Content.selectedInbound(): InboundDto? =
    inbounds.firstOrNull { it.id == selectedInboundId }

private fun inboundDisplayName(inbound: InboundDto): String =
    inbound.remark.ifBlank { "${inbound.protocol}://${inbound.port}" }

@Composable
private fun InboundDeleteConfirmDialogInline(
    inboundName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clients_inbound_delete_title, inboundName)) },
        text = { Text(stringResource(R.string.clients_inbound_delete_message)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clients_inbound_delete_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.clients_inbound_delete_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientsContent(
    uiState: ClientsUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onAddPanel: () -> Unit,
    onNavigateAdd: (inboundId: Int) -> Unit,
    onNavigateEdit: (inboundId: Int, clientKey: String) -> Unit,
    onDeleteInboundRequest: () -> Unit,
    onPopBackStack: () -> Unit = {},
) {
    val content = uiState as? ClientsUiState.Content
    val selectedInbound = content?.selectedInbound()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clients_title)) },
                navigationIcon = {
                    IconButton(onClick = onPopBackStack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (selectedInbound != null) {
                        IconButton(onClick = onDeleteInboundRequest) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.clients_inbound_cd_delete),
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
                val isSupported = selectedInbound?.protocol?.isSupportedProtocol() ?: false
                if (isSupported && selectedId != null) {
                    FloatingActionButton(
                        onClick = { onNavigateAdd(selectedId) },
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    ) {
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
                val isSupported = selectedInbound?.protocol?.isSupportedProtocol() ?: false

                // Local search state — filtered inline without touching VM
                var query by remember { mutableStateOf("") }
                val filteredClients = if (query.isBlank()) {
                    uiState.clients
                } else {
                    uiState.clients.filter { it.email.contains(query, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    // Hero section
                    item {
                        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                            Text(
                                text = selectedInbound?.let { inboundDisplayName(it) }
                                    ?: uiState.panel.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (selectedInbound != null) {
                                    "${selectedInbound.protocol.uppercase()} · ${uiState.clients.size} clients"
                                } else {
                                    "${uiState.clients.size} clients"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontSize = 13.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // Search bar
                    item {
                        SearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        )
                    }

                    // Client rows grouped in a surface card
                    if (filteredClients.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.clients_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = GroupCardShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Column {
                                    filteredClients.forEachIndexed { index, client ->
                                        ClientRow(
                                            client = client,
                                            inboundId = uiState.selectedInboundId ?: 0,
                                            isSupported = isSupported,
                                            showTopDivider = index > 0,
                                            onEdit = onNavigateEdit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = SearchBarShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.clients_search_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ClientRow(
    client: ClientConfig,
    inboundId: Int,
    isSupported: Boolean,
    showTopDivider: Boolean,
    onEdit: (inboundId: Int, clientKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expiry = classifyExpiry(client.expiryTime)
    val usedBytes = 0L // ClientConfig doesn't carry live up/down — show limit-only info
    val limitBytes = client.totalGB

    // Determine progress: if no limit, pct = 0
    val usagePct = if (limitBytes > 0L) (usedBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val isHighUsage = usagePct >= HIGH_USAGE_THRESHOLD

    // Status dot color
    val dotColor = when {
        !client.enable -> MaterialTheme.colorScheme.onSurfaceVariant
        expiry is ExpiryLabel.ExpiredAgo -> MaterialTheme.colorScheme.error
        expiry is ExpiryLabel.ExpiresIn && expiry.days < WARN_THRESHOLD_DAYS -> MaterialTheme.colorScheme.tertiary
        isHighUsage -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val clickModifier = if (isSupported) {
        modifier
            .fillMaxWidth()
            .clickable { onEdit(inboundId, client.urlKey) }
    } else {
        modifier.fillMaxWidth()
    }

    Column(modifier = clickModifier) {
        if (showTopDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Top row: dot + email + expires
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = client.email.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ExpiryText(expiry = expiry)
            }

            // Progress row
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val progressColor = if (isHighUsage) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary
                LinearProgressIndicator(
                    progress = { usagePct },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = trafficCaption(client),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExpiryText(expiry: ExpiryLabel) {
    val (text, color) = when (expiry) {
        is ExpiryLabel.Never -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        is ExpiryLabel.ExpiresIn -> {
            val warnColor = MaterialTheme.colorScheme.tertiary
            val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
            "${expiry.days} d" to if (expiry.days < WARN_THRESHOLD_DAYS) warnColor else normalColor
        }
        is ExpiryLabel.ExpiredAgo -> "expired" to MaterialTheme.colorScheme.error
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = color,
        )
    }
}

@Composable
private fun trafficCaption(client: ClientConfig): String =
    if (client.totalGB <= 0L) "∞" else prettyBytes(client.totalGB)

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
        ClientsContent(
            uiState = ClientsUiState.Content(
                panel = panel,
                inbounds = listOf(
                    InboundDto(
                        id = 1, remark = "vless-reality-443", port = 443, protocol = "vless",
                        enable = true, up = 0L, down = 0L, total = 0L, expiryTime = 0L,
                        listen = "", settings = "{\"clients\":[]}", streamSettings = "{}",
                        tag = "inbound-443", sniffing = "{}",
                    ),
                ),
                selectedInboundId = 1,
                clients = listOf(
                    ClientConfig.Vless(
                        id = "11111111-2222-3333-4444-555555555555",
                        flow = "",
                        email = "alice@studio",
                        enable = true,
                        totalGB = 50L * 1_073_741_824L,
                        expiryTime = System.currentTimeMillis() + 32L * 86_400_000L,
                        limitIp = 0,
                        subId = "",
                        comment = "",
                    ),
                    ClientConfig.Vless(
                        id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        flow = "",
                        email = "charlie@home",
                        enable = true,
                        totalGB = 50L * 1_073_741_824L,
                        expiryTime = System.currentTimeMillis() + 3L * 86_400_000L,
                        limitIp = 0,
                        subId = "",
                        comment = "",
                    ),
                    ClientConfig.Vless(
                        id = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff",
                        flow = "",
                        email = "ed@ext",
                        enable = false,
                        totalGB = 0L,
                        expiryTime = System.currentTimeMillis() - 5L * 86_400_000L,
                        limitIp = 0,
                        subId = "",
                        comment = "",
                    ),
                ),
            ),
            isRefreshing = false,
            snackbarHostState = SnackbarHostState(),
            onRefresh = {},
            onAddPanel = {},
            onNavigateAdd = {},
            onNavigateEdit = { _, _ -> },
            onDeleteInboundRequest = {},
            onPopBackStack = {},
        )
    }
}
