package com.firesin.xuipanel.feature.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.util.randomShadowsocksPassword
import com.firesin.xuipanel.core.xui.util.randomSubId
import com.firesin.xuipanel.core.xui.util.randomUuid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BYTES_PER_GB = 1_073_741_824L
private val VLESS_FLOW_OPTIONS = listOf("", "xtls-rprx-vision")
private const val SS_DEFAULT_METHOD = "chacha20-ietf-poly1305"

/**
 * Single-form screen for both add and edit.
 *
 * @param existingClient null when adding; non-null when editing (pre-fills fields).
 * @param protocol inbound protocol ("vmess", "vless", "shadowsocks").
 * @param onSubmit called with the constructed [ClientConfig] on valid submit.
 * @param onCancel called when user cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    protocol: String,
    existingClient: ClientConfig? = null,
    onSubmit: (ClientConfig) -> Unit,
    onCancel: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.client_form_cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Identity section ---
            SectionHeader(stringResource(R.string.client_form_section_identity))

            when (protocol.lowercase()) {
                "vmess", "vless" -> {
                    UuidField(
                        value = uuid,
                        onValueChange = { uuid = it },
                        readOnly = isEdit,
                        isError = identityError,
                    )
                    if (protocol.lowercase() == "vless") {
                        Spacer(Modifier.height(4.dp))
                        FlowDropdown(selected = vlessFlow, onSelected = { vlessFlow = it })
                    }
                }
                "shadowsocks" -> {
                    ShadowsocksPasswordField(
                        value = ssPassword,
                        onValueChange = { ssPassword = it },
                        isError = identityError,
                        showWarning = isEdit,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = ssMethod,
                        onValueChange = { ssMethod = it },
                        label = { Text(stringResource(R.string.client_form_method_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            // --- Email ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.client_form_email_label)) },
                isError = emailError,
                supportingText = if (emailError) {
                    { Text(stringResource(R.string.client_form_error_email_empty)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // --- Limits section ---
            SectionHeader(stringResource(R.string.client_form_section_limits))

            OutlinedTextField(
                value = totalGbText,
                onValueChange = { totalGbText = it },
                label = { Text(stringResource(R.string.client_form_total_gb_label)) },
                isError = totalGbError,
                supportingText = if (totalGbError) {
                    { Text(stringResource(R.string.client_form_error_total_gb_negative)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Expiry date picker row
            ExpiryRow(
                expiryTime = expiryTime,
                onPickDate = { showDatePicker = true },
                onClear = { expiryTime = 0L },
            )

            OutlinedTextField(
                value = limitIpText,
                onValueChange = { limitIpText = it },
                label = { Text(stringResource(R.string.client_form_limit_ip_label)) },
                isError = limitIpError,
                supportingText = if (limitIpError) {
                    { Text(stringResource(R.string.client_form_error_limit_ip_negative)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // --- Flags section ---
            SectionHeader(stringResource(R.string.client_form_section_flags))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.client_form_enable_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = enable, onCheckedChange = { enable = it })
            }

            // --- Metadata section ---
            SectionHeader(stringResource(R.string.client_form_section_meta))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = subId,
                    onValueChange = { subId = it },
                    label = { Text(stringResource(R.string.client_form_sub_id_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(onClick = { subId = randomSubId() }) {
                    Text(stringResource(R.string.client_form_generate))
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.client_form_comment_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(8.dp))

            // --- Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.client_form_cancel))
                }
                Button(
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
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isEdit) {
                            stringResource(R.string.client_form_submit_save)
                        } else {
                            stringResource(R.string.client_form_submit_add)
                        },
                    )
                }
            }
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
}

@Composable
private fun SectionHeader(text: String) {
    HorizontalDivider()
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun UuidField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    isError: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.client_form_uuid_label)) },
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.client_form_error_identity_empty)) }
            } else null,
            readOnly = readOnly,
            enabled = !readOnly,
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        if (!readOnly) {
            OutlinedButton(onClick = { onValueChange(randomUuid()) }) {
                Text(stringResource(R.string.client_form_generate))
            }
        }
    }
}

@Composable
private fun ShadowsocksPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    showWarning: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.client_form_password_label)) },
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.client_form_error_identity_empty)) }
                } else null,
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(onClick = { onValueChange(randomShadowsocksPassword()) }) {
                Text(stringResource(R.string.client_form_generate))
            }
        }
        if (showWarning) {
            Text(
                text = stringResource(R.string.client_form_password_rekey_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.ifBlank { "none" },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.client_form_flow_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VLESS_FLOW_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifBlank { "none" }) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpiryRow(
    expiryTime: Long,
    onPickDate: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.client_form_expiry_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expiryTime > 0L) {
                    formatExpiryDate(expiryTime)
                } else {
                    stringResource(R.string.client_form_expiry_no_expiry)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onPickDate) {
                Text(stringResource(R.string.client_form_expiry_pick))
            }
            if (expiryTime > 0L) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.client_form_expiry_clear))
                }
            }
        }
    }
}

private fun bytesToGbString(bytes: Long): String =
    if (bytes == 0L) "0" else (bytes / BYTES_PER_GB).toString()

private fun formatExpiryDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))

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
        )
    }
}
