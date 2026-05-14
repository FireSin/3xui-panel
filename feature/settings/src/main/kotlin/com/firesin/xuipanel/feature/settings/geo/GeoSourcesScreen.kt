package com.firesin.xuipanel.feature.settings.geo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.xui.dto.CustomGeoResourceDto
import com.firesin.xuipanel.feature.settings.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoSourcesScreen(
    onBack: () -> Unit,
    viewModel: GeoSourcesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<CustomGeoResourceDto?>(null) }
    var deleteItem by remember { mutableStateOf<CustomGeoResourceDto?>(null) }

    val deletedMsg = stringResource(R.string.geo_sources_snackbar_deleted)
    val downloadedMsg = stringResource(R.string.geo_sources_snackbar_downloaded)
    val updateAllDoneMsg = stringResource(R.string.geo_sources_snackbar_update_all_done)
    val copiedMsg = stringResource(R.string.geo_sources_aliases_copied)

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.geo_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.updateAll(updateAllDoneMsg) },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.geo_sources_action_update_all))
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is GeoSourcesUiState.Content) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.geo_sources_action_add))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            GeoSourcesUiState.NoActivePanel -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Нет активной панели",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is GeoSourcesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is GeoSourcesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            is GeoSourcesUiState.Content -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (aliases.isNotEmpty()) {
                            item(key = "aliases_section") {
                                AliasesSection(
                                    aliases = aliases,
                                    onAliasClick = { alias ->
                                        clipboardManager.setText(AnnotatedString(alias))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(copiedMsg)
                                        }
                                    },
                                )
                            }
                        }
                        if (state.items.isEmpty()) {
                            item(key = "empty_placeholder") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(R.string.geo_sources_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.geo_sources_empty_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.items, key = { it.id }) { item ->
                                GeoSourceCard(
                                    item = item,
                                    onDownload = { viewModel.download(item.id, downloadedMsg) },
                                    onEdit = { editItem = item },
                                    onDelete = { deleteItem = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGeoSourceDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { type, alias, url ->
                viewModel.add(type, alias, url, onDone = { showAddDialog = false })
            },
        )
    }

    editItem?.let { item ->
        AddGeoSourceDialog(
            initial = item,
            onDismiss = { editItem = null },
            onSave = { type, alias, url ->
                viewModel.update(item.id, type, alias, url, onDone = { editItem = null })
            },
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(R.string.geo_sources_delete_title)) },
            text = {
                Text(stringResource(R.string.geo_sources_delete_message, item.alias))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item.id, deletedMsg)
                        deleteItem = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.geo_sources_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) {
                    Text(stringResource(R.string.geo_sources_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun GeoSourceCard(
    item: CustomGeoResourceDto,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.alias,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GeoTypeChip(type = item.type)
                    Text(
                        text = item.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                val updatedText = if (item.lastUpdatedAt > 0L) {
                    stringResource(
                        R.string.geo_sources_last_updated,
                        DATE_FORMAT.format(Date(item.lastUpdatedAt * 1000L)),
                    )
                } else {
                    stringResource(R.string.geo_sources_never_updated)
                }
                Text(
                    text = updatedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.geo_sources_action_download)) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.geo_sources_action_edit)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.geo_sources_action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeoTypeChip(type: String, modifier: Modifier = Modifier) {
    val isGeoIp = type.equals("geoip", ignoreCase = true)
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = if (isGeoIp) {
                    stringResource(R.string.geo_sources_type_geoip)
                } else {
                    stringResource(R.string.geo_sources_type_geosite)
                },
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (isGeoIp) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            labelColor = if (isGeoIp) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasesSection(
    aliases: List<String>,
    onAliasClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val showAllLabel = stringResource(R.string.geo_sources_aliases_show_all, aliases.size)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.geo_sources_aliases_header),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = showAllLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    aliases.forEach { alias ->
                        SuggestionChip(
                            onClick = { onAliasClick(alias) },
                            label = {
                                Text(
                                    text = alias,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AliasesSectionPreview() {
    AliasesSection(
        aliases = listOf("geoip:cn", "geoip:private", "geosite:google", "geosite:github", "geoip:myips"),
        onAliasClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun GeoSourceCardPreview() {
    GeoSourceCard(
        item = CustomGeoResourceDto(
            id = 1,
            type = "geoip",
            alias = "myips",
            url = "https://example.com/my.dat",
            lastUpdatedAt = 1715000000L,
        ),
        onDownload = {},
        onEdit = {},
        onDelete = {},
    )
}
