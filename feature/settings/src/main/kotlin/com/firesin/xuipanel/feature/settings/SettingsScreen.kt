package com.firesin.xuipanel.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
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
                onResetAllTraffics = { viewModel.resetAllTraffics(it.first, it.second) },
                onUpdatePanel = { viewModel.updatePanel(it.first, it.second) },
                onInstallXray = { version, msgs -> viewModel.installXray(version, msgs.first, msgs.second) },
                onBackupToTgBot = { viewModel.backupToTgBot(it.first, it.second) },
            )
            Spacer(Modifier.height(8.dp))
            AboutCard(installId = installId)
        }
    }
}

// ---- System actions section ----

@Composable
private fun SystemActionsSection(
    isLoading: Boolean,
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
        SystemActionCard(
            title = stringResource(R.string.settings_update_panel_title),
            subtitle = stringResource(R.string.settings_update_panel_subtitle),
            enabled = !isLoading,
            onClick = { showUpdateDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        SystemActionCard(
            title = stringResource(R.string.settings_install_xray_title),
            subtitle = stringResource(R.string.settings_install_xray_subtitle),
            enabled = !isLoading,
            onClick = { showInstallDialog = true },
        )
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
        onResetAllTraffics = {},
        onUpdatePanel = {},
        onInstallXray = { _, _ -> },
        onBackupToTgBot = {},
    )
}
