package com.firesin.xuipanel.feature.settings.panelsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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
                    onSave = { viewModel.save(savedMsg) },
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
    flags: PanelFlags,
    onChange: ((PanelFlags) -> PanelFlags) -> Unit,
    onSave: () -> Unit,
    onChangeCreds: () -> Unit,
    onRestart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Subscription ────────────────────────────────────────────────────
        SectionCard(stringResource(R.string.panel_setup_section_sub)) {
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_enable),
                checked = flags.subEnable,
                onChange = { v -> onChange { it.copy(subEnable = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_sub_port),
                value = flags.subPort,
                onValueChange = { v -> onChange { it.copy(subPort = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_path),
                value = flags.subPath,
                onValueChange = { v -> onChange { it.copy(subPath = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_domain),
                value = flags.subDomain,
                onValueChange = { v -> onChange { it.copy(subDomain = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_uri),
                value = flags.subUri,
                onValueChange = { v -> onChange { it.copy(subUri = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_sub_updates),
                value = flags.subUpdates,
                onValueChange = { v -> onChange { it.copy(subUpdates = v) } },
            )
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_routing),
                checked = flags.subEnableRouting,
                onChange = { v -> onChange { it.copy(subEnableRouting = v) } },
            )
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_encrypt),
                checked = flags.subEncrypt,
                onChange = { v -> onChange { it.copy(subEncrypt = v) } },
            )
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_show_info),
                checked = flags.subShowInfo,
                onChange = { v -> onChange { it.copy(subShowInfo = v) } },
            )
            HorizontalDivider()
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_json_enable),
                checked = flags.subJsonEnable,
                onChange = { v -> onChange { it.copy(subJsonEnable = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_json_path),
                value = flags.subJsonPath,
                onValueChange = { v -> onChange { it.copy(subJsonPath = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_json_uri),
                value = flags.subJsonUri,
                onValueChange = { v -> onChange { it.copy(subJsonUri = v) } },
            )
            HorizontalDivider()
            ToggleRow(
                label = stringResource(R.string.panel_setup_sub_clash_enable),
                checked = flags.subClashEnable,
                onChange = { v -> onChange { it.copy(subClashEnable = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_clash_path),
                value = flags.subClashPath,
                onValueChange = { v -> onChange { it.copy(subClashPath = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_sub_clash_uri),
                value = flags.subClashUri,
                onValueChange = { v -> onChange { it.copy(subClashUri = v) } },
            )
        }

        // ── Telegram ────────────────────────────────────────────────────────
        SectionCard(stringResource(R.string.panel_setup_section_tg)) {
            ToggleRow(
                label = stringResource(R.string.panel_setup_tg_enable),
                checked = flags.tgBotEnable,
                onChange = { v -> onChange { it.copy(tgBotEnable = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_token),
                value = flags.tgBotToken,
                onValueChange = { v -> onChange { it.copy(tgBotToken = v) } },
                isPassword = true,
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_chat_id),
                value = flags.tgBotChatId,
                onValueChange = { v -> onChange { it.copy(tgBotChatId = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_proxy),
                value = flags.tgBotProxy,
                onValueChange = { v -> onChange { it.copy(tgBotProxy = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_api_server),
                value = flags.tgBotApiServer,
                onValueChange = { v -> onChange { it.copy(tgBotApiServer = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_run_time),
                value = flags.tgRunTime,
                onValueChange = { v -> onChange { it.copy(tgRunTime = v) } },
            )
            ToggleRow(
                label = stringResource(R.string.panel_setup_tg_backup),
                checked = flags.tgBotBackup,
                onChange = { v -> onChange { it.copy(tgBotBackup = v) } },
            )
            ToggleRow(
                label = stringResource(R.string.panel_setup_tg_login_notify),
                checked = flags.tgBotLoginNotify,
                onChange = { v -> onChange { it.copy(tgBotLoginNotify = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_tg_cpu),
                value = flags.tgCpu,
                onValueChange = { v -> onChange { it.copy(tgCpu = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_tg_lang),
                value = flags.tgLang,
                onValueChange = { v -> onChange { it.copy(tgLang = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_time_location),
                value = flags.timeLocation,
                onValueChange = { v -> onChange { it.copy(timeLocation = v) } },
            )
        }

        // ── Web / panel host ───────────────────────────────────────────────
        SectionCard(stringResource(R.string.panel_setup_section_web)) {
            TextField(
                label = stringResource(R.string.panel_setup_web_domain),
                value = flags.webDomain,
                onValueChange = { v -> onChange { it.copy(webDomain = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_web_port),
                value = flags.webPort,
                onValueChange = { v -> onChange { it.copy(webPort = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_web_base_path),
                value = flags.webBasePath,
                onValueChange = { v -> onChange { it.copy(webBasePath = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_web_session_max_age),
                value = flags.sessionMaxAge,
                onValueChange = { v -> onChange { it.copy(sessionMaxAge = v) } },
            )
            IntField(
                label = stringResource(R.string.panel_setup_web_page_size),
                value = flags.pageSize,
                onValueChange = { v -> onChange { it.copy(pageSize = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_web_trusted_cidrs),
                value = flags.trustedProxyCIDRs,
                onValueChange = { v -> onChange { it.copy(trustedProxyCIDRs = v) } },
            )
            TextField(
                label = stringResource(R.string.panel_setup_web_datepicker),
                value = flags.datepicker,
                onValueChange = { v -> onChange { it.copy(datepicker = v) } },
            )
        }

        // ── 2FA ────────────────────────────────────────────────────────────
        SectionCard(stringResource(R.string.panel_setup_section_2fa)) {
            ToggleRow(
                label = stringResource(R.string.panel_setup_2fa_enable),
                checked = flags.twoFactorEnable,
                onChange = { v -> onChange { it.copy(twoFactorEnable = v) } },
            )
            Text(
                text = stringResource(R.string.panel_setup_2fa_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Save / dangerous actions ────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.panel_setup_save))
                }
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
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
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
private fun TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
    )
}

@Composable
private fun IntField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
            onValueChange(parsed)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
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
