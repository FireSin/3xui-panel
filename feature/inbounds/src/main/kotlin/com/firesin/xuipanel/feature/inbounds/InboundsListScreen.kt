package com.firesin.xuipanel.feature.inbounds

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.firesin.xuipanel.core.xui.dto.InboundDto
import com.firesin.xuipanel.feature.inbounds.ui.InboundsUiState
import com.firesin.xuipanel.feature.inbounds.ui.InboundsViewModel
import java.time.Instant

@Composable
fun InboundsListScreen(
    onAddPanel: () -> Unit = {},
    onManageClients: (inboundId: Int) -> Unit = {},
    onMenuClick: () -> Unit = {},
    viewModel: InboundsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteName by remember { mutableStateOf("") }

    val resolvedError = errorMessage?.toUserMessage()
    LaunchedEffect(resolvedError) {
        resolvedError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    InboundsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onAddPanel = onAddPanel,
        onManageClients = onManageClients,
        onMenuClick = onMenuClick,
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
    onMenuClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val panel = (uiState as? InboundsUiState.Content)?.panel
                        ?: (uiState as? InboundsUiState.Error)?.panel
                        ?: (uiState as? InboundsUiState.Loading)?.panel
                    Column {
                        Text(
                            text = panel?.name ?: stringResource(R.string.inbounds_title),
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
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.inbounds_cd_menu_open),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (uiState) {
            is InboundsUiState.NoActivePanel -> NoActivePanelEmpty(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddPanel = onAddPanel,
            )

            is InboundsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is InboundsUiState.Error -> ErrorState(
                error = uiState.error,
                onRetry = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is InboundsUiState.Content -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (uiState.inbounds.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.inbounds_empty),
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
                        items(uiState.inbounds, key = { it.id }) { inbound ->
                            InboundCard(
                                inbound = inbound,
                                onManageClients = { onManageClients(inbound.id) },
                            )
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onManageClients),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inbound.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${inbound.port} · ${inbound.protocol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.inbounds_traffic,
                        formatBytes(inbound.up),
                        formatBytes(inbound.down),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = inbound.enable,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun InboundDto.displayName(): String =
    remark.ifBlank { "#${port} ${protocol}" }

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
            text = stringResource(R.string.inbounds_no_active_panel),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPanel) {
            Text(stringResource(R.string.inbounds_add_panel))
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
            Text(stringResource(R.string.inbounds_retry))
        }
    }
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.inbounds_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.inbounds_error_tls, message)
    is DomainError.Network -> stringResource(R.string.inbounds_error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.inbounds_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.inbounds_error_response, body)
    is DomainError.Unexpected -> stringResource(R.string.inbounds_error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.inbounds_error_pin_mismatch)
}

@Preview(showBackground = true)
@Composable
private fun InboundsContentPreview() {
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
        InboundsContent(
            uiState = InboundsUiState.Content(
                panel = panel,
                inbounds = listOf(
                    InboundDto(
                        id = 1,
                        remark = "VMess-443",
                        port = 443,
                        protocol = "vmess",
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
                        remark = "",
                        port = 8080,
                        protocol = "trojan",
                        enable = false,
                        up = 0L,
                        down = 0L,
                        total = 0L,
                        expiryTime = 0L,
                        listen = "",
                        settings = "{}",
                        streamSettings = "{}",
                        tag = "inbound-8080",
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
private fun InboundsLoadingPreview() {
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
