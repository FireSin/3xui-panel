package com.firesin.xuipanel.feature.settings.apitokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.xui.dto.ApiTokenDto
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelApiTokensScreen(
    onBack: () -> Unit,
    viewModel: PanelApiTokensViewModel = hiltViewModel(),
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiTokenDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_tokens_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.api_tokens_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is PanelApiTokensUiState.Content || state is PanelApiTokensUiState.Error) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.api_tokens_create))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is PanelApiTokensUiState.NoActivePanel -> CenterText(stringResource(R.string.api_tokens_no_active_panel))
                is PanelApiTokensUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is PanelApiTokensUiState.Error -> CenterText(s.error.toString())
                is PanelApiTokensUiState.Content -> TokenList(
                    panel = s.panel,
                    tokens = s.tokens,
                    onToggle = viewModel::setEnabled,
                    onAskDelete = { pendingDelete = it },
                )
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTokenDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.create(name) { showCreateDialog = false }
            },
        )
    }

    pendingDelete?.let { token ->
        val panel = (state as? PanelApiTokensUiState.Content)?.panel
        val isAppToken = panel?.apiToken == token.token
        val deletedMsg = stringResource(R.string.api_tokens_deleted)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.api_tokens_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.api_tokens_delete_message, token.name))
                    if (isAppToken) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.api_tokens_delete_app_warning),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(token.id, deletedMsg)
                    pendingDelete = null
                }) { Text(stringResource(R.string.api_tokens_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.api_tokens_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun TokenList(
    panel: Panel,
    tokens: List<ApiTokenDto>,
    onToggle: (Int, Boolean) -> Unit,
    onAskDelete: (ApiTokenDto) -> Unit,
) {
    if (tokens.isEmpty()) {
        CenterText(stringResource(R.string.api_tokens_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tokens, key = { it.id }) { token ->
            TokenCard(
                token = token,
                isAppToken = panel.apiToken == token.token,
                onToggle = { onToggle(token.id, it) },
                onDelete = { onAskDelete(token) },
            )
        }
    }
}

@Composable
private fun TokenCard(
    token: ApiTokenDto,
    isAppToken: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(checked = token.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.api_tokens_delete))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = maskToken(token.token),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(token.token))
                },
            )
            if (isAppToken) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.api_tokens_app_token_chip)) },
                )
            }
        }
    }
}

@Composable
private fun CreateTokenDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_tokens_create_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.api_tokens_create_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.api_tokens_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.api_tokens_delete_cancel))
            }
        },
    )
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun maskToken(token: String): String {
    if (token.length <= 10) return token
    return token.take(4) + "…" + token.takeLast(4)
}
