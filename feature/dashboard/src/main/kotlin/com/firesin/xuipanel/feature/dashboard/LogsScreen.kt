package com.firesin.xuipanel.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.designsystem.component.ErrorState
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.feature.dashboard.ui.LogsSource
import com.firesin.xuipanel.feature.dashboard.ui.LogsUiState
import com.firesin.xuipanel.feature.dashboard.ui.LogsViewModel

@Composable
fun LogsScreen(
    onPopBackStack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()

    LogsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        source = source,
        onSourceChange = viewModel::setSource,
        onRefresh = viewModel::refresh,
        onPopBackStack = onPopBackStack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsContent(
    uiState: LogsUiState,
    isRefreshing: Boolean,
    source: LogsSource,
    onSourceChange: (LogsSource) -> Unit,
    onRefresh: () -> Unit,
    onPopBackStack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onPopBackStack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.logs_refresh_cd),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SegmentedPicker(
                options = LogsSource.entries,
                selected = source,
                onSelect = onSourceChange,
                label = { it.toLabel() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            when (uiState) {
                is LogsUiState.NoActivePanel -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.logs_no_active_panel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is LogsUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is LogsUiState.Error -> ErrorState(
                    title = stringResource(R.string.logs_error_title),
                    description = uiState.error.toUserMessage(),
                    actionLabel = stringResource(R.string.logs_retry),
                    onAction = onRefresh,
                )

                is LogsUiState.Content -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (uiState.lines.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.logs_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(uiState.lines) { line ->
                                Text(
                                    text = line,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsSource.toLabel(): String = when (this) {
    LogsSource.PANEL -> stringResource(R.string.logs_source_panel)
    LogsSource.XRAY -> stringResource(R.string.logs_source_xray)
}

@Composable
private fun DomainError.toUserMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.logs_error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.logs_error_tls, message)
    is DomainError.Network -> stringResource(R.string.logs_error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.logs_error_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.logs_error_response, body)
    is DomainError.Unexpected -> stringResource(R.string.logs_error_unexpected)
    is DomainError.PinMismatch -> stringResource(R.string.logs_error_pin_mismatch)
    is DomainError.NotFound -> message
}
