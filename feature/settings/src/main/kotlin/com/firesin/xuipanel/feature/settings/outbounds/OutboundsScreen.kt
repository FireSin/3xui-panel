package com.firesin.xuipanel.feature.settings.outbounds

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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.designsystem.format.formatBytes
import com.firesin.xuipanel.core.xui.dto.OutboundTrafficDto
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundsScreen(
    onBack: () -> Unit,
    viewModel: OutboundsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val xrayResult by viewModel.xrayResult.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    var pendingResetTag by remember { mutableStateOf<String?>(null) }
    val resetDoneMsg = stringResource(R.string.outbounds_reset_done)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.outbounds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadXrayResult() }) {
                        Icon(Icons.Filled.Article, contentDescription = stringResource(R.string.outbounds_xray_output))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.outbounds_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                OutboundsUiState.NoActivePanel -> Centered { Text(stringResource(R.string.outbounds_no_active_panel)) }
                is OutboundsUiState.Loading -> Centered { CircularProgressIndicator() }
                is OutboundsUiState.Error -> Centered { Text(s.error.toString()) }
                is OutboundsUiState.Content -> OutboundsList(
                    items = s.outbounds,
                    onReset = { pendingResetTag = it },
                    onTest = { viewModel.testTag(it) },
                )
            }
            if (busy) Centered { CircularProgressIndicator() }
        }
    }

    pendingResetTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingResetTag = null },
            title = { Text(stringResource(R.string.outbounds_reset_title)) },
            text = { Text(stringResource(R.string.outbounds_reset_message, tag)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetTag(tag, resetDoneMsg)
                    pendingResetTag = null
                }) { Text(stringResource(R.string.outbounds_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetTag = null }) {
                    Text(stringResource(R.string.outbounds_cancel))
                }
            },
        )
    }

    testResult?.let { res ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestResult() },
            title = { Text(stringResource(R.string.outbounds_test_title, res.tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (res.success) {
                        Text(
                            stringResource(R.string.outbounds_test_ok, res.delayMs),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            stringResource(R.string.outbounds_test_fail),
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (res.error.isNotBlank()) {
                            Text(text = res.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (res.mode.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.outbounds_test_mode, res.mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTestResult() }) {
                    Text(stringResource(R.string.outbounds_close))
                }
            },
        )
    }

    xrayResult?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissXrayResult() },
            title = { Text(stringResource(R.string.outbounds_xray_output)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissXrayResult() }) {
                    Text(stringResource(R.string.outbounds_close))
                }
            },
        )
    }
}

@Composable
private fun OutboundsList(
    items: List<OutboundTrafficDto>,
    onReset: (String) -> Unit,
    onTest: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.outbounds_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.isEmpty()) {
            item { Text(stringResource(R.string.outbounds_empty)) }
        } else {
            items(items, key = { it.id }) { item ->
                OutboundCard(
                    item = item,
                    onReset = { onReset(item.tag) },
                    onTest = { onTest(item.tag) },
                )
            }
        }
    }
}

@Composable
private fun OutboundCard(item: OutboundTrafficDto, onReset: () -> Unit, onTest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.tag.isTestableOutbound()) {
                    IconButton(onClick = onTest) {
                        Icon(
                            Icons.Filled.NetworkCheck,
                            contentDescription = stringResource(R.string.outbounds_test),
                        )
                    }
                }
                IconButton(onClick = onReset) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.outbounds_reset),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                StatColumn(
                    label = stringResource(R.string.outbounds_up),
                    value = formatBytes(item.up),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.outbounds_down),
                    value = formatBytes(item.down),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.outbounds_total),
                    value = formatBytes(item.total),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// Special routing outbounds in xray (no remote endpoint to probe).
private fun String.isTestableOutbound(): Boolean =
    this.lowercase() !in setOf("direct", "block", "blocked", "blackhole", "api", "dns-out")
