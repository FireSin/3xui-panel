@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firesin.xuipanel.feature.panels

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.panels.ui.ConfirmImportDialogState
import com.firesin.xuipanel.feature.panels.ui.ExportDialogState
import com.firesin.xuipanel.feature.panels.ui.ImportDialogState
import com.firesin.xuipanel.feature.panels.ui.PanelsListEvent
import com.firesin.xuipanel.feature.panels.ui.PanelsListUiState
import com.firesin.xuipanel.feature.panels.ui.PanelsListViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@Composable
fun PanelsListScreen(
    onAddPanel: () -> Unit,
    onEditPanel: (String) -> Unit,
    onPanelSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: PanelsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingDeletePanel by remember { mutableStateOf<Panel?>(null) }

    val resolvedError = errorMessage?.toUserMessage()
    LaunchedEffect(resolvedError) {
        resolvedError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    // SAF: create document (export)
    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onSafCreateResult(uri)
    }

    // SAF: open document (import)
    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onSafOpenResult(uri)
    }

    // One-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PanelsListEvent.OpenSafCreate -> {
                    exportSafLauncher.launch(event.suggestedFileName)
                }
                is PanelsListEvent.OpenSafOpen -> {
                    importSafLauncher.launch(arrayOf("application/json", "*/*"))
                }
                is PanelsListEvent.WriteToUri -> {
                    runCatching {
                        context.contentResolver.openOutputStream(event.uri)?.use { stream ->
                            stream.write(event.content.toByteArray(Charsets.UTF_8))
                        }
                    }.onFailure {
                        snackbarHostState.showSnackbar("Ошибка записи файла")
                    }
                }
                is PanelsListEvent.ReadFromUri -> {
                    val maxBytes = 1024 * 1024
                    val fileSize: Long? = context.contentResolver
                        .query(event.uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                        ?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else null
                        }
                    if (fileSize != null && fileSize > maxBytes) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.panels_backup_file_too_large),
                        )
                    } else {
                        runCatching {
                            context.contentResolver.openInputStream(event.uri)?.use { stream ->
                                val buffer = ByteArray(8 * 1024)
                                val out = java.io.ByteArrayOutputStream()
                                var total = 0
                                while (true) {
                                    val n = stream.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    if (total > maxBytes) {
                                        throw IllegalStateException("File exceeds 1 MB limit")
                                    }
                                    out.write(buffer, 0, n)
                                }
                                out.toByteArray().toString(Charsets.UTF_8)
                            } ?: throw IllegalStateException("Cannot open stream")
                        }.fold(
                            onSuccess = { viewModel.onFileContentRead(it) },
                            onFailure = { err ->
                                if (err.message?.contains("exceeds") == true) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.panels_backup_file_too_large),
                                    )
                                } else {
                                    viewModel.onFileReadError()
                                }
                            },
                        )
                    }
                }
                is PanelsListEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val contentState = uiState as? PanelsListUiState.Content

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
        onExportClick = viewModel::onExportClick,
        onImportClick = viewModel::onImportClick,
        onNavigateToSettings = onNavigateToSettings,
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

    contentState?.exportDialog?.let { dialog ->
        ExportPassphraseDialog(
            dialog = dialog,
            onPasswordChange = viewModel::onExportPasswordChange,
            onConfirmChange = viewModel::onExportConfirmChange,
            onDismiss = viewModel::onExportDialogDismiss,
            onConfirm = viewModel::onExportConfirm,
        )
    }

    contentState?.importDialog?.let { dialog ->
        if (dialog.envelopeJson != null) {
            ImportPassphraseDialog(
                dialog = dialog,
                onPasswordChange = viewModel::onImportPasswordChange,
                onDismiss = viewModel::onImportDialogDismiss,
                onConfirm = viewModel::onImportConfirm,
            )
        }
    }

    contentState?.confirmImportDialog?.let { confirm ->
        ConfirmImportDialog(
            existingCount = confirm.existingCount,
            onDismiss = viewModel::onConfirmImportDismiss,
            onProceed = viewModel::onConfirmImportProceed,
        )
    }
}

@Composable
private fun PanelsListContent(
    uiState: PanelsListUiState,
    snackbarHostState: SnackbarHostState,
    onAddPanel: () -> Unit,
    onPanelTap: (Panel) -> Unit,
    onEditPanel: (String) -> Unit,
    onDeletePanel: (Panel) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

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
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.panels_cd_overflow_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.panels_overflow_export)) },
                                onClick = {
                                    overflowExpanded = false
                                    onExportClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.panels_overflow_import)) },
                                onClick = {
                                    overflowExpanded = false
                                    onImportClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.panels_overflow_settings)) },
                                onClick = {
                                    overflowExpanded = false
                                    onNavigateToSettings()
                                },
                            )
                        }
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
                    if (panel.tlsMode == TlsMode.PINNED) {
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
private fun ExportPassphraseDialog(
    dialog: ExportDialogState,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isValid = dialog.password.length >= PanelsListViewModel.MIN_PASSPHRASE_LENGTH &&
        dialog.password == dialog.confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panels_export_dialog_title)) },
        text = {
            Column {
                BackupPasswordField(
                    value = dialog.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.panels_passphrase_label),
                )
                Spacer(Modifier.height(8.dp))
                BackupPasswordField(
                    value = dialog.confirm,
                    onValueChange = onConfirmChange,
                    label = stringResource(R.string.panels_passphrase_confirm_label),
                    isError = dialog.confirm.isNotEmpty() && dialog.password != dialog.confirm,
                )
                if (dialog.password.isNotEmpty() && dialog.password.length < PanelsListViewModel.MIN_PASSPHRASE_LENGTH) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.panels_passphrase_too_short, PanelsListViewModel.MIN_PASSPHRASE_LENGTH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = isValid) {
                Text(stringResource(R.string.panels_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.panels_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ImportPassphraseDialog(
    dialog: ImportDialogState,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panels_import_dialog_title)) },
        text = {
            BackupPasswordField(
                value = dialog.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.panels_passphrase_label),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.password.isNotEmpty()) {
                Text(stringResource(R.string.panels_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.panels_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ConfirmImportDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panels_confirm_import_title)) },
        text = {
            Text(stringResource(R.string.panels_confirm_import_message, existingCount))
        },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text(stringResource(R.string.panels_confirm_import_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.panels_dialog_cancel))
            }
        },
    )
}

@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.error_tls, message)
    is DomainError.Network -> stringResource(R.string.error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.error_panel_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.error_unexpected)
    is DomainError.Unexpected -> stringResource(R.string.error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.error_pin_mismatch)
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
                tlsMode = TlsMode.SYSTEM,
                pinnedSpkiSha256 = null,
                pinnedAt = null,
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
                tlsMode = TlsMode.PINNED,
                pinnedSpkiSha256 = "abc123",
                pinnedAt = Instant.now(),
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
            onExportClick = {},
            onImportClick = {},
            onNavigateToSettings = {},
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
            onExportClick = {},
            onImportClick = {},
            onNavigateToSettings = {},
        )
    }
}
