package com.firesin.xuipanel.feature.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.panels.ui.PanelsListUiState
import com.firesin.xuipanel.feature.panels.ui.PanelsListViewModel
import java.time.Instant

@Composable
fun PanelsListScreen(
    onAddPanel: () -> Unit,
    onEditPanel: (String) -> Unit,
    onPanelSelected: (String) -> Unit,
    viewModel: PanelsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDeletePanel by remember { mutableStateOf<Panel?>(null) }

    val resolvedError = errorMessage?.toUserMessage()
    LaunchedEffect(resolvedError) {
        resolvedError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    PanelsListContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAddPanel = onAddPanel,
        onPanelTap = { panel ->
            viewModel.setActive(panel.id)
            onPanelSelected(panel.id)
        },
        onEditPanel = onEditPanel,
        onDeletePanel = { panel -> pendingDeletePanel = panel },
    )

    pendingDeletePanel?.let { panel ->
        PanelDeleteConfirmDialog(
            panelName = panel.name,
            onConfirm = {
                viewModel.delete(panel.id)
                pendingDeletePanel = null
            },
            onDismiss = { pendingDeletePanel = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelsListContent(
    uiState: PanelsListUiState,
    snackbarHostState: SnackbarHostState,
    onAddPanel: () -> Unit,
    onPanelTap: (Panel) -> Unit,
    onEditPanel: (String) -> Unit,
    onDeletePanel: (Panel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.panels_title)) },
                actions = {
                    IconButton(onClick = onAddPanel) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.panels_add),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (uiState) {
            is PanelsListUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is PanelsListUiState.Content -> {
                if (uiState.panels.isEmpty()) {
                    PanelsEmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onAddPanel = onAddPanel,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(uiState.panels, key = { it.id }) { panel ->
                            PanelCard(
                                panel = panel,
                                isActive = panel.id == uiState.active?.id,
                                onTap = { onPanelTap(panel) },
                                onEdit = { onEditPanel(panel.id) },
                                onDelete = { onDeletePanel(panel) },
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelsEmptyState(
    onAddPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.panels_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAddPanel) {
            Text(stringResource(R.string.panels_empty_action))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelCard(
    panel: Panel,
    isActive: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuExpanded = true },
            ),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = panel.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (panel.trustSelfSigned) {
                        Icon(
                            imageVector = Icons.Default.ShieldMoon,
                            contentDescription = stringResource(R.string.panels_cd_trust_self_signed),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = stringResource(R.string.panels_badge_active),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = panel.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.panels_cd_panel_menu),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.panels_menu_edit)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.panels_menu_delete)) },
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

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.error_tls, message)
    is DomainError.Network -> stringResource(R.string.error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.error_panel_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.error_unexpected)
    is DomainError.Unexpected -> stringResource(R.string.error_unexpected)
}

@Preview(showBackground = true)
@Composable
private fun PanelsListContentPreview() {
    XuiPanelTheme {
        val panels = listOf(
            Panel(
                id = "1",
                name = "Мой сервер",
                baseUrl = "https://panel.example.com:2053",
                login = "admin",
                password = "pass",
                trustSelfSigned = false,
                isActive = true,
                createdAt = Instant.now(),
                lastLoginAt = null,
            ),
            Panel(
                id = "2",
                name = "Резервный",
                baseUrl = "https://backup.example.com:2053",
                login = "admin",
                password = "pass",
                trustSelfSigned = true,
                isActive = false,
                createdAt = Instant.now(),
                lastLoginAt = null,
            ),
        )
        PanelsListContent(
            uiState = PanelsListUiState.Content(panels = panels, active = panels.first()),
            snackbarHostState = SnackbarHostState(),
            onAddPanel = {},
            onPanelTap = {},
            onEditPanel = {},
            onDeletePanel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PanelsEmptyPreview() {
    XuiPanelTheme {
        PanelsListContent(
            uiState = PanelsListUiState.Content(panels = emptyList(), active = null),
            snackbarHostState = SnackbarHostState(),
            onAddPanel = {},
            onPanelTap = {},
            onEditPanel = {},
            onDeletePanel = {},
        )
    }
}
