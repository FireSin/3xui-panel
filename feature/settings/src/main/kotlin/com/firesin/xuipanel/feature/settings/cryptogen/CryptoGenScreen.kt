package com.firesin.xuipanel.feature.settings.cryptogen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoGenScreen(
    onBack: () -> Unit,
    viewModel: CryptoGenViewModel = hiltViewModel(),
) {
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var showEchDialog by remember { mutableStateOf(false) }
    val titleX25519 = stringResource(R.string.crypto_gen_x25519)
    val titleMldsa = stringResource(R.string.crypto_gen_mldsa)
    val titleMlkem = stringResource(R.string.crypto_gen_mlkem)
    val titleVless = stringResource(R.string.crypto_gen_vless_enc)
    val titleEch = stringResource(R.string.crypto_gen_ech)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crypto_gen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.crypto_gen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GeneratorButton(text = titleX25519) { viewModel.generateX25519(titleX25519) }
                GeneratorButton(text = titleVless) { viewModel.generateVlessEnc(titleVless) }
                GeneratorButton(text = titleMldsa) { viewModel.generateMldsa65(titleMldsa) }
                GeneratorButton(text = titleMlkem) { viewModel.generateMlkem768(titleMlkem) }
                GeneratorButton(text = titleEch) { showEchDialog = true }
            }
            if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showEchDialog) {
        EchDialog(
            onDismiss = { showEchDialog = false },
            onConfirm = { sni ->
                viewModel.generateEchCert(sni, titleEch)
                showEchDialog = false
            },
        )
    }

    result?.let { res ->
        ResultDialog(result = res, onDismiss = { viewModel.consumeResult() })
    }
}

@Composable
private fun GeneratorButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text) }
}

@Composable
private fun EchDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var sni by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crypto_gen_ech_dialog_title)) },
        text = {
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text(stringResource(R.string.crypto_gen_ech_sni)) },
                placeholder = { Text("example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sni.trim()) },
                enabled = sni.isNotBlank(),
            ) { Text(stringResource(R.string.crypto_gen_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.crypto_gen_cancel)) }
        },
    )
}

@Composable
private fun ResultDialog(result: KeyResult, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(result.lines) { (label, value) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clipboard.setText(AnnotatedString(value)) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.crypto_gen_close)) }
        },
    )
}
