package com.firesin.xuipanel.feature.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.addExpiry
import com.firesin.xuipanel.core.common.util.addExpiryMonths
import com.firesin.xuipanel.core.common.util.formatRemainingTime
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.util.randomShadowsocksPassword
import com.firesin.xuipanel.core.xui.util.randomSubId
import com.firesin.xuipanel.core.xui.util.randomUuid
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel.ClientIpsState
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel.SubLinksState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BYTES_PER_GB = 1_073_741_824L
private val VLESS_FLOW_OPTIONS = listOf("", "xtls-rprx-vision")
private const val SS_DEFAULT_METHOD = "chacha20-ietf-poly1305"
private const val MS_PER_DAY = 86_400_000L

/**
 * Single-form screen for both add and edit.
 *
 * @param existingClient null when adding; non-null when editing (pre-fills fields).
 * @param protocol inbound protocol ("vmess", "vless", "shadowsocks").
 * @param onSubmit called with the constructed [ClientConfig] on valid submit.
 * @param onCancel called when user cancels.
 * @param onShare called when user taps "Поделиться" (edit mode only).
 * @param onResetTraffic called when user confirms "Сбросить трафик" (edit mode only).
 * @param onDelete called when user confirms "Удалить" (edit mode only); caller does popBackStack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    protocol: String,
    existingClient: ClientConfig? = null,
    onSubmit: (ClientConfig) -> Unit,
    onCancel: () -> Unit,
    onShare: (() -> Unit)? = null,
    onResetTraffic: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    clientIpsState: ClientIpsState = ClientIpsState.Idle,
    onLoadIps: () -> Unit = {},
    onClearIps: () -> Unit = {},
    subLinksState: SubLinksState = SubLinksState.Idle,
    onLoadSubLinks: (subId: String) -> Unit = {},
    onDismissSubLinks: () -> Unit = {},
) {
    val isEdit = existingClient != null
    val title = if (isEdit) {
        stringResource(R.string.client_form_title_edit)
    } else {
        stringResource(R.string.client_form_title_add)
    }

    // --- Identity state ---
    var uuid by rememberSaveable {
        mutableStateOf(
            when (existingClient) {
                is ClientConfig.Vmess -> existingClient.id
                is ClientConfig.Vless -> existingClient.id
                else -> ""
            },
        )
    }
    var ssPassword by rememberSaveable {
        mutableStateOf(
            (existingClient as? ClientConfig.Shadowsocks)?.password ?: "",
        )
    }
    var ssMethod by rememberSaveable {
        mutableStateOf(
            (existingClient as? ClientConfig.Shadowsocks)?.method?.ifBlank { SS_DEFAULT_METHOD }
                ?: SS_DEFAULT_METHOD,
        )
    }
    var vlessFlow by rememberSaveable {
        mutableStateOf(
            (existingClient as? ClientConfig.Vless)?.flow ?: "",
        )
    }

    // --- Common fields ---
    var email by rememberSaveable { mutableStateOf(existingClient?.email ?: "") }
    var totalGbText by rememberSaveable {
        mutableStateOf(
            existingClient?.let { bytesToGbString(it.totalGB) } ?: "0",
        )
    }
    var expiryTime by rememberSaveable {
        mutableLongStateOf(existingClient?.expiryTime ?: 0L)
    }
    var limitIpText by rememberSaveable {
        mutableStateOf(existingClient?.limitIp?.toString() ?: "0")
    }
    var enable by rememberSaveable { mutableStateOf(existingClient?.enable ?: true) }
    var subId by rememberSaveable { mutableStateOf(existingClient?.subId ?: "") }
    var comment by rememberSaveable { mutableStateOf(existingClient?.comment ?: "") }

    // --- Validation ---
    val identityError by remember {
        derivedStateOf {
            when (protocol.lowercase()) {
                "shadowsocks" -> ssPassword.isBlank()
                else -> uuid.isBlank()
            }
        }
    }
    val emailError by remember { derivedStateOf { email.isBlank() } }
    val totalGbError by remember { derivedStateOf { totalGbText.toLongOrNull()?.let { it < 0 } ?: true } }
    val limitIpError by remember { derivedStateOf { limitIpText.toIntOrNull()?.let { it < 0 } ?: true } }
    val isSubmitEnabled = !identityError && !emailError && !totalGbError && !limitIpError

    // --- DatePicker ---
    var showDatePicker by remember { mutableStateOf(false) }

    // --- Overflow menu state ---
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.client_form_cancel),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val totalGbBytes = (totalGbText.toLongOrNull() ?: 0L) * BYTES_PER_GB
                            val limitIp = limitIpText.toIntOrNull() ?: 0
                            val config = buildClientConfig(
                                protocol = protocol,
                                existingClient = existingClient,
                                uuid = uuid,
                                ssPassword = ssPassword,
                                ssMethod = ssMethod,
                                vlessFlow = vlessFlow,
                                email = email,
                                totalGB = totalGbBytes,
                                expiryTime = expiryTime,
                                limitIp = limitIp,
                                enable = enable,
                                subId = subId,
                                comment = comment,
                            )
                            onSubmit(config)
                        },
                        enabled = isSubmitEnabled,
                    ) {
                        Text(
                            text = if (isEdit) {
                                stringResource(R.string.client_form_submit_save)
                            } else {
                                stringResource(R.string.client_form_submit_add)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isSubmitEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    if (isEdit) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.client_form_cd_more),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                if (onShare != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.client_form_menu_share)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onShare()
                                        },
                                    )
                                }
                                if (onResetTraffic != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.client_form_menu_reset_traffic)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showResetConfirm = true
                                        },
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.client_form_menu_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirm = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── IDENTITY ──────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.client_form_section_identity))
            when (protocol.lowercase()) {
                "vmess", "vless" -> {
                    GroupCard {
                        FieldRow(
                            label = stringResource(R.string.client_form_email_label),
                            value = email,
                            onValueChange = { email = it },
                            isError = emailError,
                            topDivider = false,
                            stacked = true,
                        )
                        FieldRow(
                            label = stringResource(R.string.client_form_uuid_label),
                            value = uuid,
                            onValueChange = { uuid = it },
                            monoValue = true,
                            isError = identityError,
                            readOnly = isEdit,
                            enabled = !isEdit,
                            trailing = if (!isEdit) {
                                {
                                    TextButton(onClick = { uuid = randomUuid() }) {
                                        Text(
                                            stringResource(R.string.client_form_generate),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            } else null,
                        )
                        if (protocol.lowercase() == "vless") {
                            FlowSelectorRow(
                                selected = vlessFlow,
                                onSelected = { vlessFlow = it },
                            )
                        }
                    }
                }
                "shadowsocks" -> {
                    GroupCard {
                        FieldRow(
                            label = stringResource(R.string.client_form_email_label),
                            value = email,
                            onValueChange = { email = it },
                            isError = emailError,
                            topDivider = false,
                            stacked = true,
                        )
                        FieldRow(
                            label = stringResource(R.string.client_form_password_label),
                            value = ssPassword,
                            onValueChange = { ssPassword = it },
                            isError = identityError,
                            trailing = {
                                TextButton(onClick = { ssPassword = randomShadowsocksPassword() }) {
                                    Text(
                                        stringResource(R.string.client_form_generate),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                        )
                        FieldRow(
                            label = stringResource(R.string.client_form_method_label),
                            value = ssMethod,
                            onValueChange = { ssMethod = it },
                        )
                    }
                    if (isEdit) {
                        Text(
                            text = stringResource(R.string.client_form_password_rekey_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── LIMITS ────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.client_form_section_limits))
            GroupCard {
                FieldRow(
                    label = stringResource(R.string.client_form_total_gb_label_short),
                    value = totalGbText,
                    onValueChange = { totalGbText = it },
                    isError = totalGbError,
                    topDivider = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailing = if (totalGbError) {
                        null
                    } else null,
                )
                // Expiry row inside GroupCard
                ExpiryFieldRow(
                    expiryTime = expiryTime,
                    onPickDate = { showDatePicker = true },
                    onClear = { expiryTime = 0L },
                )
                FieldRow(
                    label = stringResource(R.string.client_form_limit_ip_label_short),
                    value = limitIpText,
                    onValueChange = { limitIpText = it },
                    isError = limitIpError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // Quick-preset chips (below GroupCard, outside card)
            ExpiryChipsRow(
                expiryTime = expiryTime,
                onSetExpiryTime = { expiryTime = it },
            )

            Spacer(Modifier.height(12.dp))

            // ── STATE ─────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.client_form_section_state))
            GroupCard(footer = stringResource(R.string.client_form_state_footer)) {
                FieldRow(
                    label = stringResource(R.string.client_form_enable_label),
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    topDivider = false,
                    trailing = {
                        IosToggle(
                            checked = enable,
                            onCheckedChange = { enable = it },
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── META ──────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.client_form_section_meta))
            GroupCard {
                FieldRow(
                    label = stringResource(R.string.client_form_sub_id_label),
                    value = subId,
                    onValueChange = { subId = it },
                    monoValue = true,
                    topDivider = false,
                    stacked = true,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            if (isEdit && subId.isNotBlank()) {
                                TextButton(onClick = { onLoadSubLinks(subId) }) {
                                    Text(
                                        text = stringResource(R.string.client_form_sub_links),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            TextButton(onClick = { subId = randomSubId() }) {
                                Text(
                                    stringResource(R.string.client_form_generate),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                )
                FieldRow(
                    label = stringResource(R.string.client_form_comment_label),
                    value = comment,
                    onValueChange = { comment = it },
                    stacked = true,
                    singleLineValue = false,
                )
            }

            if (isEdit) {
                Spacer(Modifier.height(12.dp))
                SectionHeader(stringResource(R.string.client_form_section_ips))
                ClientIpsSection(
                    state = clientIpsState,
                    onLoad = onLoadIps,
                    onClear = onClearIps,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (expiryTime > 0L) expiryTime else null,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryTime = datePickerState.selectedDateMillis ?: 0L
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.client_form_expiry_pick))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.client_form_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResetConfirm && onResetTraffic != null) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.client_form_reset_title)) },
            text = { Text(stringResource(R.string.client_form_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetTraffic()
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.client_form_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.client_form_reset_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.client_form_delete_title)) },
            text = { Text(stringResource(R.string.client_form_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.client_form_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.client_form_delete_cancel))
                }
            },
        )
    }

    if (subLinksState !is SubLinksState.Idle) {
        SubLinksDialog(state = subLinksState, onDismiss = onDismissSubLinks)
    }
}

// ── Subscription links dialog ─────────────────────────────────────────────────

@Composable
private fun SubLinksDialog(state: SubLinksState, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.client_form_sub_links_title)) },
        text = {
            when (state) {
                SubLinksState.Idle, SubLinksState.Loading -> {
                    Text(stringResource(R.string.client_form_sub_links_loading))
                }
                is SubLinksState.Loaded -> {
                    if (state.links.isEmpty()) {
                        Text(stringResource(R.string.client_form_sub_links_empty))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.links.forEach { url ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = MonoFontFamily,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                    }) {
                                        Text(
                                            text = stringResource(R.string.client_form_sub_links_copy),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is SubLinksState.Error -> {
                    Text(stringResource(R.string.client_form_sub_links_error))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.client_form_sub_links_close))
            }
        },
    )
}

// ── Client IPs section (edit mode only) ──────────────────────────────────────

@Composable
private fun ClientIpsSection(
    state: ClientIpsState,
    onLoad: () -> Unit,
    onClear: () -> Unit,
) {
    GroupCard(footer = stringResource(R.string.client_form_ips_footer)) {
        when (state) {
            ClientIpsState.Idle, ClientIpsState.Loading -> {
                IpInfoRow(text = stringResource(R.string.client_form_ips_loading))
            }
            is ClientIpsState.Loaded -> {
                if (state.ips.isEmpty()) {
                    IpInfoRow(text = stringResource(R.string.client_form_ips_empty))
                } else {
                    state.ips.forEachIndexed { index, ip ->
                        IpRow(ip = ip, topDivider = index != 0)
                    }
                    IpClearRow(onClear = onClear)
                }
            }
            is ClientIpsState.Error -> {
                IpInfoRow(
                    text = stringResource(R.string.client_form_ips_error),
                    trailing = {
                        TextButton(onClick = onLoad) {
                            Text(
                                text = stringResource(R.string.client_form_ips_retry),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IpRow(ip: String, topDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Text(
            text = ip,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonoFontFamily,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun IpInfoRow(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
private fun IpClearRow(onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.client_form_ips_clear),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Flow selector row (for VLESS) ─────────────────────────────────────────────

@Composable
private fun FlowSelectorRow(
    selected: String,
    onSelected: (String) -> Unit,
) {
    FieldRow(
        label = stringResource(R.string.client_form_flow_label),
        value = selected.ifBlank { stringResource(R.string.client_form_flow_none) },
        onValueChange = {},
        readOnly = true,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                VLESS_FLOW_OPTIONS.forEach { option ->
                    if (option != selected) {
                        TextButton(onClick = { onSelected(option) }) {
                            Text(
                                text = option.ifBlank { stringResource(R.string.client_form_flow_none) },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}

// ── Expiry field row (inside GroupCard) ───────────────────────────────────────

@Composable
private fun ExpiryFieldRow(
    expiryTime: Long,
    onPickDate: () -> Unit,
    onClear: () -> Unit,
) {
    val remaining = formatRemainingTime(expiryTime)
    val expiryLabel = if (expiryTime > 0L) {
        val dateStr = formatExpiryDate(expiryTime)
        if (remaining != null) "$dateStr ($remaining)" else dateStr
    } else {
        stringResource(R.string.client_form_expiry_no_expiry)
    }

    FieldRow(
        label = stringResource(R.string.client_form_expiry_label),
        value = expiryLabel,
        onValueChange = {},
        readOnly = true,
        trailing = {
            if (expiryTime > 0L) {
                TextButton(onClick = onClear) {
                    Text(
                        stringResource(R.string.client_form_expiry_clear),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                TextButton(onClick = onPickDate) {
                    Text(
                        stringResource(R.string.client_form_expiry_pick),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

// ── Quick-preset expiry chips ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpiryChipsRow(
    expiryTime: Long,
    onSetExpiryTime: (Long) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(7, 10, 14, 20).forEach { days ->
            AssistChip(
                onClick = {
                    onSetExpiryTime(addExpiry(expiryTime, days * MS_PER_DAY))
                },
                label = { Text("+${days}д") },
            )
        }
        listOf(1 to "+1 мес", 3 to "+3 мес", 6 to "+6 мес", 12 to "+12 мес").forEach { (months, label) ->
            AssistChip(
                onClick = {
                    onSetExpiryTime(addExpiryMonths(expiryTime, months))
                },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = expiryTime == 0L,
            onClick = { onSetExpiryTime(0L) },
            label = { Text(stringResource(R.string.client_form_expiry_no_limit_chip)) },
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun bytesToGbString(bytes: Long): String =
    if (bytes == 0L) "0" else (bytes / BYTES_PER_GB).toString()

private fun formatExpiryDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun buildClientConfig(
    protocol: String,
    existingClient: ClientConfig?,
    uuid: String,
    ssPassword: String,
    ssMethod: String,
    vlessFlow: String,
    email: String,
    totalGB: Long,
    expiryTime: Long,
    limitIp: Int,
    enable: Boolean,
    subId: String,
    comment: String,
): ClientConfig {
    val now = System.currentTimeMillis()
    val createdAt = existingClient?.createdAt ?: now
    val updatedAt = if (existingClient != null) now else null

    return when (protocol.lowercase()) {
        "vless" -> ClientConfig.Vless(
            id = uuid,
            flow = vlessFlow,
            email = email,
            enable = enable,
            totalGB = totalGB,
            expiryTime = expiryTime,
            limitIp = limitIp,
            subId = subId,
            comment = comment,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        "shadowsocks" -> ClientConfig.Shadowsocks(
            password = ssPassword,
            method = ssMethod,
            email = email,
            enable = enable,
            totalGB = totalGB,
            expiryTime = expiryTime,
            limitIp = limitIp,
            subId = subId,
            comment = comment,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        else -> ClientConfig.Vmess(
            id = uuid,
            email = email,
            enable = enable,
            totalGB = totalGB,
            expiryTime = expiryTime,
            limitIp = limitIp,
            subId = subId,
            comment = comment,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ClientFormAddVmessPreview() {
    XuiPanelTheme {
        ClientFormScreen(
            protocol = "vmess",
            existingClient = null,
            onSubmit = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClientFormEditVlessPreview() {
    XuiPanelTheme {
        ClientFormScreen(
            protocol = "vless",
            existingClient = ClientConfig.Vless(
                id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                flow = "xtls-rprx-vision",
                email = "user@example.com",
                enable = true,
                totalGB = 10L * BYTES_PER_GB,
                expiryTime = 0L,
                limitIp = 2,
                subId = "sub123",
                comment = "",
            ),
            onSubmit = {},
            onCancel = {},
            onShare = {},
            onResetTraffic = {},
            onDelete = {},
        )
    }
}
