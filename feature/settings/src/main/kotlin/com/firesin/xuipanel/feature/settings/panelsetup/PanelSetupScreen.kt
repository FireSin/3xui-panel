package com.firesin.xuipanel.feature.settings.panelsetup

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelSetupScreen(
    onBack: () -> Unit,
    viewModel: PanelSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var showChangeCreds by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.panel_setup_saved)
    val restartingMsg = stringResource(R.string.panel_setup_restart_started)
    val credsDoneMsg = stringResource(R.string.panel_setup_creds_changed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.panel_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                PanelSetupUiState.NoActivePanel -> Centered { Text(stringResource(R.string.panel_setup_no_active_panel)) }
                is PanelSetupUiState.Loading -> Centered { CircularProgressIndicator() }
                is PanelSetupUiState.Error -> Centered { Text(s.error.toString()) }
                is PanelSetupUiState.Content -> Content(
                    flags = s.flags,
                    onChange = viewModel::updateFlags,
                    onSave = { viewModel.saveSubscription(savedMsg) },
                    onChangeCreds = { showChangeCreds = true },
                    onRestart = { showRestartConfirm = true },
                )
            }
            if (busy) Centered { CircularProgressIndicator() }
        }
    }

    if (showChangeCreds) {
        ChangeCredentialsDialog(
            onDismiss = { showChangeCreds = false },
            onSubmit = { user, pass ->
                viewModel.changeCredentials(user, pass, credsDoneMsg)
                showChangeCreds = false
            },
        )
    }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text(stringResource(R.string.panel_setup_restart_title)) },
            text = { Text(stringResource(R.string.panel_setup_restart_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restartPanel(restartingMsg)
                    showRestartConfirm = false
                }) { Text(stringResource(R.string.panel_setup_restart_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(stringResource(R.string.panel_setup_cancel))
                }
            },
        )
    }
}

@Composable
private fun Content(
    flags: SubFlags,
    onChange: ((SubFlags) -> SubFlags) -> Unit,
    onSave: () -> Unit,
    onChangeCreds: () -> Unit,
    onRestart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        // Subscription section
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.panel_setup_section_sub), style = MaterialTheme.typography.titleMedium)

                ToggleRow(
                    label = stringResource(R.string.panel_setup_sub_enable),
                    checked = flags.enable,
                    onChange = { v -> onChange { it.copy(enable = v) } },
                )
                OutlinedTextField(
                    value = flags.uri,
                    onValueChange = { v -> onChange { it.copy(uri = v) } },
                    label = { Text(stringResource(R.string.panel_setup_sub_uri)) },
                    placeholder = { Text("https://example.com:31102/subfs/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                HorizontalDivider()

                ToggleRow(
                    label = stringResource(R.string.panel_setup_sub_json_enable),
                    checked = flags.jsonEnable,
                    onChange = { v -> onChange { it.copy(jsonEnable = v) } },
                )
                OutlinedTextField(
                    value = flags.jsonUri,
                    onValueChange = { v -> onChange { it.copy(jsonUri = v) } },
                    label = { Text(stringResource(R.string.panel_setup_sub_json_uri)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                HorizontalDivider()

                ToggleRow(
                    label = stringResource(R.string.panel_setup_sub_clash_enable),
                    checked = flags.clashEnable,
                    onChange = { v -> onChange { it.copy(clashEnable = v) } },
                )
                OutlinedTextField(
                    value = flags.clashUri,
                    onValueChange = { v -> onChange { it.copy(clashUri = v) } },
                    label = { Text(stringResource(R.string.panel_setup_sub_clash_uri)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.panel_setup_save))
                }
            }
        }

        // Dangerous actions
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.panel_setup_section_admin), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onChangeCreds, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.panel_setup_change_creds))
                }
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.panel_setup_restart))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChangeCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panel_setup_change_creds_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.panel_setup_change_creds_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.panel_setup_new_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.panel_setup_new_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(user.trim(), pass) },
                enabled = user.isNotBlank() && pass.isNotBlank(),
            ) { Text(stringResource(R.string.panel_setup_change_creds_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_setup_cancel)) }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
