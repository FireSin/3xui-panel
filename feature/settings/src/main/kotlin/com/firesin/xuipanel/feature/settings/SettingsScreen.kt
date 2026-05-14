package com.firesin.xuipanel.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.data.repository.AutoBackupSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    onNavigateToGeoSources: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val lockToggleState by viewModel.lockToggleState.collectAsStateWithLifecycle()
    val lockOnPauseEnabled by viewModel.lockOnPauseEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val installId by viewModel.installId.collectAsStateWithLifecycle()
    val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()
    val xrayVersion by viewModel.xrayVersion.collectAsStateWithLifecycle()
    val panelUpdateInfo by viewModel.panelUpdateInfo.collectAsStateWithLifecycle()
    val autoBackupSchedule by viewModel.autoBackupSchedule.collectAsStateWithLifecycle()
    val autoBackupFolderName by viewModel.autoBackupFolderName.collectAsStateWithLifecycle()
    val autoBackupLastRunAt by viewModel.autoBackupLastRunAt.collectAsStateWithLifecycle()
    val autoBackupLastResult by viewModel.autoBackupLastResult.collectAsStateWithLifecycle()
    val autoBackupTargetUri by viewModel.autoBackupTargetUri.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var configJsonDialog by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val dbDownloadSuccess = stringResource(R.string.settings_backup_download_db_success)
    val dbDownloadError = stringResource(R.string.settings_backup_download_db_error)
    val dbImportSuccess = stringResource(R.string.settings_backup_import_success)
    val dbImportError = stringResource(R.string.settings_backup_import_error)
    val configError = stringResource(R.string.settings_backup_view_config_error)

    // SAF launcher: user picks destination for DB download
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.downloadDb(uri)
    }

    // SAF launcher: user picks existing DB file for import
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importDb(uri)
    }

    // SAF launcher: user picks folder for auto-backup
    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onAutoBackupFolderPicked(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { event ->
            when (event) {
                is SettingsViewModel.BackupEvent.DbDownloaded ->
                    snackbarHostState.showSnackbar(dbDownloadSuccess)
                is SettingsViewModel.BackupEvent.DbRestored ->
                    snackbarHostState.showSnackbar(dbImportSuccess)
                is SettingsViewModel.BackupEvent.GeofileUpdated -> Unit // handled by snackbarMessage
                is SettingsViewModel.BackupEvent.ConfigLoaded ->
                    configJsonDialog = event.json
                is SettingsViewModel.BackupEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Load server info on first composition
    LaunchedEffect(Unit) {
        viewModel.loadXrayVersion()
        viewModel.loadPanelUpdateInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LockCard(
                state = lockToggleState,
                onToggle = viewModel::setLockEnabled,
            )
            Spacer(Modifier.height(8.dp))
            LockOnPauseCard(
                enabled = lockOnPauseEnabled,
                mainLockActive = (lockToggleState as? LockToggleState.Available)?.enabled == true,
                onToggle = viewModel::setLockOnPauseEnabled,
            )
            Spacer(Modifier.height(8.dp))
            ThemeCard(
                selected = themeMode,
                onSelect = viewModel::setThemeMode,
            )
            Spacer(Modifier.height(8.dp))
            GeoSourcesCard(onClick = onNavigateToGeoSources)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SystemActionsSection(
                isLoading = isActionLoading,
                xrayVersion = xrayVersion,
                panelUpdateInfo = panelUpdateInfo,
                onResetAllTraffics = { viewModel.resetAllTraffics(it.first, it.second) },
                onUpdatePanel = { viewModel.updatePanel(it.first, it.second) },
                onInstallXray = { version, msgs -> viewModel.installXray(version, msgs.first, msgs.second) },
                onBackupToTgBot = { viewModel.backupToTgBot(it.first, it.second) },
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            BackupSection(
                isLoading = isActionLoading,
                onDownloadDb = {
                    createDocumentLauncher.launch(context.getString(R.string.settings_backup_db_filename))
                },
                onImportDb = { openDocumentLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                onUpdateGeofile = {
                    viewModel.updateGeofile(
                        successMsg = context.getString(R.string.settings_backup_update_geofile_success),
                        errorPrefix = context.getString(R.string.settings_backup_update_geofile_error),
                    )
                },
                onViewConfig = { viewModel.loadConfigJson() },
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            AutoBackupSection(
                schedule = autoBackupSchedule,
                folderName = autoBackupFolderName,
                hasFolderSelected = autoBackupTargetUri != null,
                lastRunAt = autoBackupLastRunAt,
                lastResult = autoBackupLastResult,
                onScheduleChange = viewModel::setAutoBackupSchedule,
                onChooseFolder = { openTreeLauncher.launch(null) },
                onTriggerNow = viewModel::triggerAutoBackupNow,
            )
            Spacer(Modifier.height(8.dp))
            AboutCard(installId = installId)
        }
    }

    // Config JSON read-only dialog
    configJsonDialog?.let { json ->
        ConfigJsonDialog(
            json = json,
            onDismiss = { configJsonDialog = null },
        )
    }
}

// ---- System actions section ----

@Composable
private fun SystemActionsSection(
    isLoading: Boolean,
    xrayVersion: String?,
    panelUpdateInfo: PanelUpdateState,
    onResetAllTraffics: (Pair<String, String>) -> Unit,
    onUpdatePanel: (Pair<String, String>) -> Unit,
    onInstallXray: (String, Pair<String, String>) -> Unit,
    onBackupToTgBot: (Pair<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val successReset = stringResource(R.string.settings_reset_traffics_success)
    val errorReset = stringResource(R.string.settings_reset_traffics_error)
    val successUpdate = stringResource(R.string.settings_update_panel_success)
    val errorUpdate = stringResource(R.string.settings_update_panel_error)
    val successInstall = stringResource(R.string.settings_install_xray_success)
    val errorInstall = stringResource(R.string.settings_install_xray_error)
    val successBackup = stringResource(R.string.settings_backup_tg_success)
    val errorBackup = stringResource(R.string.settings_backup_tg_error)

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallDialog by rememberSaveable { mutableStateOf(false) }
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }

    val updateAvailable = (panelUpdateInfo as? PanelUpdateState.Loaded)?.info?.isUpdatable == true
    val latestVersion = (panelUpdateInfo as? PanelUpdateState.Loaded)?.info?.latestVersion

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_system_actions_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Loading indicator — shown while updatePanel or installXray is running
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        SystemActionCard(
            title = stringResource(R.string.settings_reset_traffics_title),
            subtitle = stringResource(R.string.settings_reset_traffics_subtitle),
            enabled = !isLoading,
            onClick = { showResetDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        // "Обновить панель" — shows red badge dot and latest version label when update available
        BadgedBox(
            badge = {
                if (updateAvailable) {
                    Badge()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            SystemActionCard(
                title = stringResource(R.string.settings_update_panel_title),
                subtitle = if (updateAvailable && latestVersion != null) {
                    stringResource(R.string.settings_panel_update_available, latestVersion)
                } else {
                    stringResource(R.string.settings_update_panel_subtitle)
                },
                enabled = !isLoading,
                onClick = { showUpdateDialog = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        // "Установить Xray" — shows current version as a chip below the card
        SystemActionCard(
            title = stringResource(R.string.settings_install_xray_title),
            subtitle = stringResource(R.string.settings_install_xray_subtitle),
            enabled = !isLoading,
            onClick = { showInstallDialog = true },
        )
        if (xrayVersion != null) {
            Spacer(Modifier.height(4.dp))
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = "${stringResource(R.string.settings_xray_version_label)}: $xrayVersion",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        SystemActionCard(
            title = stringResource(R.string.settings_backup_tg_title),
            subtitle = stringResource(R.string.settings_backup_tg_subtitle),
            enabled = !isLoading,
            onClick = { showBackupDialog = true },
        )
    }

    // Dialogs

    if (showResetDialog) {
        ResetTrafficsDialog(
            onConfirm = {
                showResetDialog = false
                onResetAllTraffics(successReset to errorReset)
            },
            onDismiss = { showResetDialog = false },
        )
    }

    if (showUpdateDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.settings_update_panel_dialog_title),
            message = stringResource(R.string.settings_update_panel_dialog_msg),
            confirmLabel = stringResource(R.string.settings_update_panel_confirm),
            onConfirm = {
                showUpdateDialog = false
                onUpdatePanel(successUpdate to errorUpdate)
            },
            onDismiss = { showUpdateDialog = false },
        )
    }

    if (showInstallDialog) {
        InstallXrayDialog(
            onConfirm = { version ->
                showInstallDialog = false
                onInstallXray(version, successInstall to errorInstall)
            },
            onDismiss = { showInstallDialog = false },
        )
    }

    if (showBackupDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.settings_backup_tg_dialog_title),
            message = stringResource(R.string.settings_backup_tg_dialog_msg),
            confirmLabel = stringResource(R.string.settings_backup_tg_confirm),
            onConfirm = {
                showBackupDialog = false
                onBackupToTgBot(successBackup to errorBackup)
            },
            onDismiss = { showBackupDialog = false },
        )
    }
}

@Composable
private fun SystemActionCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
    }
}

/** Two-step confirmation dialog for the global traffic reset (extra destructive). */
@Composable
private fun ResetTrafficsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmed by rememberSaveable { mutableStateOf(false) }

    if (!confirmed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_reset_traffics_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_traffics_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = { confirmed = true }) {
                    Text(stringResource(R.string.settings_reset_traffics_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_system_cancel))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_reset_traffics_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_traffics_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.settings_reset_traffics_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_system_cancel))
                }
            },
        )
    }
}

/** Generic one-step confirmation dialog. */
@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_system_cancel))
            }
        },
    )
}

/** Install-Xray dialog with a version text field. */
@Composable
private fun InstallXrayDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var version by rememberSaveable { mutableStateOf("") }
    val emptyError = stringResource(R.string.settings_install_xray_version_empty)
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_install_xray_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_install_xray_dialog_msg))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = version,
                    onValueChange = {
                        version = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.settings_install_xray_version_hint)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(emptyError) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (version.isBlank()) {
                        showError = true
                    } else {
                        onConfirm(version.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.settings_install_xray_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_system_cancel))
            }
        },
    )
}

// ---- Backup and config section (Bundle B) ----

@Composable
private fun BackupSection(
    isLoading: Boolean,
    onDownloadDb: () -> Unit,
    onImportDb: () -> Unit,
    onUpdateGeofile: () -> Unit,
    onViewConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showGeofileDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_backup_section_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        BackupActionCard(
            title = stringResource(R.string.settings_backup_download_db_title),
            subtitle = stringResource(R.string.settings_backup_download_db_subtitle),
            enabled = !isLoading,
            onClick = onDownloadDb,
        )
        Spacer(Modifier.height(8.dp))
        BackupActionCard(
            title = stringResource(R.string.settings_backup_import_db_title),
            subtitle = stringResource(R.string.settings_backup_import_db_subtitle),
            enabled = !isLoading,
            onClick = { showImportDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        BackupActionCard(
            title = stringResource(R.string.settings_backup_update_geofile_title),
            subtitle = stringResource(R.string.settings_backup_update_geofile_subtitle),
            enabled = !isLoading,
            onClick = { showGeofileDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        BackupActionCard(
            title = stringResource(R.string.settings_backup_view_config_title),
            subtitle = stringResource(R.string.settings_backup_view_config_subtitle),
            enabled = !isLoading,
            onClick = onViewConfig,
        )
    }

    if (showImportDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.settings_backup_import_db_dialog_title),
            message = stringResource(R.string.settings_backup_import_db_dialog_msg),
            confirmLabel = stringResource(R.string.settings_backup_import_db_confirm),
            onConfirm = {
                showImportDialog = false
                onImportDb()
            },
            onDismiss = { showImportDialog = false },
        )
    }

    if (showGeofileDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.settings_backup_update_geofile_dialog_title),
            message = stringResource(R.string.settings_backup_update_geofile_dialog_msg),
            confirmLabel = stringResource(R.string.settings_backup_update_geofile_confirm),
            onConfirm = {
                showGeofileDialog = false
                onUpdateGeofile()
            },
            onDismiss = { showGeofileDialog = false },
        )
    }
}

/** Card style for backup actions — uses surfaceVariant instead of errorContainer. */
@Composable
private fun BackupActionCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 0.7f else 0.4f,
                ),
            )
        }
    }
}

/** Scrollable read-only dialog showing the raw Xray config JSON. */
@Composable
private fun ConfigJsonDialog(
    json: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.settings_backup_view_config_copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_view_config_dialog_title)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(json))
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(stringResource(R.string.settings_backup_view_config_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_backup_view_config_close))
            }
        },
    )
}

// ---- Existing cards ----

@Composable
private fun GeoSourcesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_geo_sources_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_geo_sources_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutCard(
    installId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val copiedMessage = stringResource(R.string.settings_about_install_id_copied)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_about_version),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installId.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(AnnotatedString(installId))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_install_id),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "…${installId.takeLast(12)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFontFamily,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            SegmentedPicker(
                options = ThemeMode.entries,
                selected = selected,
                onSelect = onSelect,
                label = { mode ->
                    when (mode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LockCard(
    state: LockToggleState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_lock_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_lock_card_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = (state as? LockToggleState.Available)?.enabled ?: false,
                    onCheckedChange = onToggle,
                    enabled = state is LockToggleState.Available,
                )
            }
            if (state is LockToggleState.Unavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LockOnPauseCard(
    enabled: Boolean,
    mainLockActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_lock_on_pause_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_lock_on_pause_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = mainLockActive,
            )
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true)
@Composable
private fun ThemeCardPreview() {
    ThemeCard(selected = ThemeMode.SYSTEM, onSelect = {})
}

@Preview(showBackground = true)
@Composable
private fun LockOnPauseCardPreview() {
    LockOnPauseCard(enabled = true, mainLockActive = true, onToggle = {})
}

@Preview(showBackground = true)
@Composable
private fun LockCardAvailablePreview() {
    LockCard(
        state = LockToggleState.Available(enabled = true),
        onToggle = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun LockCardUnavailablePreview() {
    LockCard(
        state = LockToggleState.Unavailable(reason = "Настройте PIN в системных настройках"),
        onToggle = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SystemActionsSectionPreview() {
    SystemActionsSection(
        isLoading = false,
        xrayVersion = "v25.5.16",
        panelUpdateInfo = PanelUpdateState.Idle,
        onResetAllTraffics = {},
        onUpdatePanel = {},
        onInstallXray = { _, _ -> },
        onBackupToTgBot = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SystemActionsSectionUpdateAvailablePreview() {
    SystemActionsSection(
        isLoading = false,
        xrayVersion = "v25.5.16",
        panelUpdateInfo = PanelUpdateState.Loaded(
            com.firesin.xuipanel.core.xui.dto.PanelUpdateInfoObj(
                currentVersion = "2.3.12",
                latestVersion = "2.3.14",
                isUpdatable = true,
            ),
        ),
        onResetAllTraffics = {},
        onUpdatePanel = {},
        onInstallXray = { _, _ -> },
        onBackupToTgBot = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SystemActionsSectionLoadingPreview() {
    SystemActionsSection(
        isLoading = true,
        xrayVersion = null,
        panelUpdateInfo = PanelUpdateState.Idle,
        onResetAllTraffics = {},
        onUpdatePanel = {},
        onInstallXray = { _, _ -> },
        onBackupToTgBot = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun BackupSectionPreview() {
    BackupSection(
        isLoading = false,
        onDownloadDb = {},
        onImportDb = {},
        onUpdateGeofile = {},
        onViewConfig = {},
    )
}

// ---- Auto backup section ----

private val DISPLAY_TIMESTAMP_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
private fun AutoBackupSection(
    schedule: AutoBackupSchedule,
    folderName: String?,
    hasFolderSelected: Boolean,
    lastRunAt: Long?,
    lastResult: String?,
    onScheduleChange: (AutoBackupSchedule) -> Unit,
    onChooseFolder: () -> Unit,
    onTriggerNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_autobackup_section_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Schedule picker
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SegmentedSchedulePicker(
                    schedule = schedule,
                    onSelect = onScheduleChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Folder row
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_autobackup_folder_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = folderName ?: stringResource(R.string.settings_autobackup_folder_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = onChooseFolder) {
                    Text(stringResource(R.string.settings_autobackup_folder_choose))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Last run status
        val lastRunText = if (lastRunAt != null && lastResult != null) {
            val dateStr = DISPLAY_TIMESTAMP_FMT.format(
                Instant.ofEpochMilli(lastRunAt).atZone(ZoneId.systemDefault()),
            )
            stringResource(R.string.settings_autobackup_last_run, "$dateStr — $lastResult")
        } else {
            stringResource(R.string.settings_autobackup_last_run_never)
        }
        Text(
            text = lastRunText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Trigger now button
        Button(
            onClick = onTriggerNow,
            enabled = hasFolderSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_autobackup_trigger_now))
        }
    }
}

@Composable
private fun SegmentedSchedulePicker(
    schedule: AutoBackupSchedule,
    onSelect: (AutoBackupSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedPicker(
        options = AutoBackupSchedule.entries,
        selected = schedule,
        onSelect = onSelect,
        label = { s ->
            when (s) {
                AutoBackupSchedule.OFF -> stringResource(R.string.settings_autobackup_schedule_off)
                AutoBackupSchedule.DAILY -> stringResource(R.string.settings_autobackup_schedule_daily)
                AutoBackupSchedule.WEEKLY -> stringResource(R.string.settings_autobackup_schedule_weekly)
            }
        },
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun AutoBackupSectionPreview() {
    AutoBackupSection(
        schedule = AutoBackupSchedule.DAILY,
        folderName = "Backups",
        hasFolderSelected = true,
        lastRunAt = System.currentTimeMillis() - 3_600_000L,
        lastResult = "Success(3)",
        onScheduleChange = {},
        onChooseFolder = {},
        onTriggerNow = {},
    )
}
