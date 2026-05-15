package com.firesin.xuipanel.feature.settings.xraymetrics

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.xui.dto.XrayMetricsStateDto
import com.firesin.xuipanel.core.xui.dto.XrayObservatoryEntryDto
import com.firesin.xuipanel.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayMetricsScreen(
    onBack: () -> Unit,
    viewModel: XrayMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.xray_metrics_title)) },
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.panel == null -> Centered { Text(stringResource(R.string.xray_metrics_no_active_panel)) }
                state.isLoading && state.metricsState == null -> Centered { CircularProgressIndicator() }
                state.error != null && state.metricsState == null -> Centered { Text(state.error.toString()) }
                else -> Content(
                    metricsState = state.metricsState,
                    observatory = state.observatory,
                )
            }
        }
    }
}

@Composable
private fun Content(
    metricsState: XrayMetricsStateDto?,
    observatory: List<XrayObservatoryEntryDto>,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricsCard(metricsState)
        ObservatoryCard(observatory)
    }
}

@Composable
private fun MetricsCard(state: XrayMetricsStateDto?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state?.enabled == true) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    text = stringResource(R.string.xray_metrics_section_state),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when {
                state == null -> Text(stringResource(R.string.xray_metrics_unknown))
                state.enabled -> {
                    Text(stringResource(R.string.xray_metrics_enabled))
                    if (state.listen.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.xray_metrics_listen, state.listen),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Text(
                        text = stringResource(R.string.xray_metrics_history_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(stringResource(R.string.xray_metrics_disabled))
                    state.reason?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.xray_metrics_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservatoryCard(entries: List<XrayObservatoryEntryDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.xray_metrics_section_observatory),
                style = MaterialTheme.typography.titleMedium,
            )
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.xray_metrics_observatory_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEachIndexed { idx, entry ->
                    if (idx > 0) HorizontalDivider()
                    ObservatoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ObservatoryRow(entry: XrayObservatoryEntryDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = entry.outbound, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (entry.alive) {
                    stringResource(R.string.xray_metrics_observatory_alive, entry.delay)
                } else {
                    stringResource(R.string.xray_metrics_observatory_dead)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.alive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
