package com.firesin.xuipanel.feature.settings.warpnord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpNordScreen(
    onBack: () -> Unit,
    viewModel: WarpNordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var showWarpLicense by remember { mutableStateOf(false) }
    var showWarpDeleteConfirm by remember { mutableStateOf(false) }
    var showNordKey by remember { mutableStateOf(false) }
    var showNordDeleteConfirm by remember { mutableStateOf(false) }
    val warpLicenseDoneMsg = stringResource(R.string.warp_nord_warp_license_done)
    val warpDeletedMsg = stringResource(R.string.warp_nord_warp_deleted)
    val nordKeyDoneMsg = stringResource(R.string.warp_nord_nord_key_done)
    val nordDeletedMsg = stringResource(R.string.warp_nord_nord_deleted)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_nord_title)) },
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
            if (state.panel == null) {
                Centered { Text(stringResource(R.string.warp_nord_no_active_panel)) }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(
                        title = stringResource(R.string.warp_nord_warp_title),
                        configured = state.warpData.isNotBlank(),
                        data = state.warpData,
                        onSetLicense = { showWarpLicense = true },
                        setLicenseLabel = stringResource(R.string.warp_nord_warp_set_license),
                        onDelete = { showWarpDeleteConfirm = true },
                        deleteLabel = stringResource(R.string.warp_nord_warp_delete),
                        emptyHint = stringResource(R.string.warp_nord_warp_empty_hint),
                    )
                    SectionCard(
                        title = stringResource(R.string.warp_nord_nord_title),
                        configured = state.nordData.isNotBlank(),
                        data = state.nordData,
                        onSetLicense = { showNordKey = true },
                        setLicenseLabel = stringResource(R.string.warp_nord_nord_set_key),
                        onDelete = { showNordDeleteConfirm = true },
                        deleteLabel = stringResource(R.string.warp_nord_nord_delete),
                        emptyHint = stringResource(R.string.warp_nord_nord_empty_hint),
                    )
                }
            }
            if (state.isBusy) Centered { CircularProgressIndicator() }
        }
    }

    if (showWarpLicense) {
        InputDialog(
            title = stringResource(R.string.warp_nord_warp_set_license),
            label = stringResource(R.string.warp_nord_warp_license_label),
            onDismiss = { showWarpLicense = false },
            onConfirm = {
                viewModel.setWarpLicense(it, warpLicenseDoneMsg)
                showWarpLicense = false
            },
        )
    }
    if (showNordKey) {
        InputDialog(
            title = stringResource(R.string.warp_nord_nord_set_key),
            label = stringResource(R.string.warp_nord_nord_key_label),
            onDismiss = { showNordKey = false },
            onConfirm = {
                viewModel.setNordKey(it, nordKeyDoneMsg)
                showNordKey = false
            },
        )
    }
    if (showWarpDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.warp_nord_warp_delete),
            text = stringResource(R.string.warp_nord_warp_delete_confirm),
            onDismiss = { showWarpDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteWarp(warpDeletedMsg)
                showWarpDeleteConfirm = false
            },
        )
    }
    if (showNordDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.warp_nord_nord_delete),
            text = stringResource(R.string.warp_nord_nord_delete_confirm),
            onDismiss = { showNordDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteNord(nordDeletedMsg)
                showNordDeleteConfirm = false
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    configured: Boolean,
    data: String,
    setLicenseLabel: String,
    onSetLicense: () -> Unit,
    deleteLabel: String,
    onDelete: () -> Unit,
    emptyHint: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (configured) {
                Text(
                    text = data,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedButton(onClick = onSetLicense, modifier = Modifier.fillMaxWidth()) {
                    Text(setLicenseLabel)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onSetLicense, modifier = Modifier.fillMaxWidth()) {
                    Text(setLicenseLabel)
                }
            }
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }, enabled = input.isNotBlank()) {
                Text(stringResource(R.string.warp_nord_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.warp_nord_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.warp_nord_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.warp_nord_cancel)) }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
