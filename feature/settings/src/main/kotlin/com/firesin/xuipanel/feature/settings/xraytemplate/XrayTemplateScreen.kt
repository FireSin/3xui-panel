package com.firesin.xuipanel.feature.settings.xraytemplate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayTemplateScreen(
    onBack: () -> Unit,
    viewModel: XrayTemplateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var pendingMetricsEnable by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.xray_template_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.pretty.isNotBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(state.pretty))
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.panel == null -> Centered { Text(stringResource(R.string.xray_template_no_active_panel)) }
                state.isLoading && state.pretty.isBlank() -> Centered { CircularProgressIndicator() }
                state.error != null && state.pretty.isBlank() -> Centered { Text(state.error.toString()) }
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WarningCard()
                    MetricsCard(
                        enabled = state.metricsEnabled,
                        canMutate = state.xraySetting != null && !state.isBusy,
                        onEnable = { pendingMetricsEnable = true },
                        onDisable = { pendingMetricsEnable = false },
                    )
                    if (state.outboundTestUrl.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.xray_template_outbound_test_url, state.outboundTestUrl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = state.pretty,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (state.isBusy) Centered { CircularProgressIndicator() }
        }
    }

    pendingMetricsEnable?.let { enable ->
        AlertDialog(
            onDismissRequest = { pendingMetricsEnable = null },
            title = {
                Text(
                    stringResource(
                        if (enable) R.string.xray_template_metrics_confirm_enable_title
                        else R.string.xray_template_metrics_confirm_disable_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (enable) R.string.xray_template_metrics_confirm_enable_message
                        else R.string.xray_template_metrics_confirm_disable_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMetricsEnable = null
                    if (enable) viewModel.enableMetrics() else viewModel.disableMetrics()
                }) {
                    Text(stringResource(R.string.xray_template_metrics_confirm_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMetricsEnable = null }) {
                    Text(stringResource(R.string.xray_template_metrics_cancel))
                }
            },
        )
    }
}

@Composable
private fun MetricsCard(
    enabled: Boolean,
    canMutate: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.xray_template_metrics_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (enabled) R.string.xray_template_metrics_status_on
                    else R.string.xray_template_metrics_status_off,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.xray_template_metrics_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                OutlinedButton(onClick = onDisable, enabled = canMutate) {
                    Text(stringResource(R.string.xray_template_metrics_disable))
                }
            } else {
                Button(onClick = onEnable, enabled = canMutate) {
                    Text(stringResource(R.string.xray_template_metrics_enable))
                }
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = stringResource(R.string.xray_template_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
