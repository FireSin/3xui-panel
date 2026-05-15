@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firesin.xuipanel.feature.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.GroupRow
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.panels.ui.AuthMode
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditUiState
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditViewModel
import com.firesin.xuipanel.feature.panels.ui.PanelFormErrors
import com.firesin.xuipanel.feature.panels.ui.PanelFormState
import com.firesin.xuipanel.feature.panels.ui.PinMismatchDialogState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PINNED_AT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun PanelAddEditScreen(
    onSaved: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: PanelAddEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is PanelAddEditUiState.Saved) onSaved()
    }

    PanelAddEditContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onNameChange = viewModel::updateName,
        onBaseUrlChange = viewModel::updateBaseUrl,
        onLoginChange = viewModel::updateLogin,
        onPasswordChange = viewModel::updatePassword,
        onTlsModeChange = viewModel::updateTlsMode,
        onAuthModeChange = viewModel::updateAuthMode,
        onApiTokenChange = viewModel::updateApiToken,
        onTwoFactorCodeChange = viewModel::updateTwoFactorCode,
        onSubmit = viewModel::submit,
        onConfirmRePin = viewModel::confirmRePin,
        onDismissPinMismatch = viewModel::dismissPinMismatchDialog,
        onRePinVerifiedChange = viewModel::updateRePinVerifiedCheckbox,
        onRequestRePin = viewModel::requestRePin,
        onDeletePanel = viewModel::deleteCurrentPanel,
    )
}

@Composable
private fun PanelAddEditContent(
    uiState: PanelAddEditUiState,
    onNavigateUp: () -> Unit,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTlsModeChange: (TlsMode) -> Unit,
    onAuthModeChange: (AuthMode) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onTwoFactorCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onConfirmRePin: () -> Unit,
    onDismissPinMismatch: () -> Unit,
    onRePinVerifiedChange: (Boolean) -> Unit,
    onRequestRePin: () -> Unit,
    onDeletePanel: () -> Unit,
) {
    val isSaving = uiState is PanelAddEditUiState.Saving
    val form = when (uiState) {
        is PanelAddEditUiState.Editing -> uiState.form
        is PanelAddEditUiState.Saving -> uiState.form
        is PanelAddEditUiState.Saved -> PanelFormState()
    }
    val errors = (uiState as? PanelAddEditUiState.Editing)?.errors
    val submitError = (uiState as? PanelAddEditUiState.Editing)?.submitError
    val isEditMode = (uiState as? PanelAddEditUiState.Editing)?.isEditMode ?: false
    val pinMismatchDialog = (uiState as? PanelAddEditUiState.Editing)?.pinMismatchDialog

    val title = if (isEditMode) {
        stringResource(R.string.panel_edit_title)
    } else {
        stringResource(R.string.panel_add_title)
    }

    val isFormValid = errors?.let { e ->
        e.name == null && e.baseUrl == null && e.apiToken == null &&
            e.login == null && e.password == null &&
            form.name.isNotBlank() && form.baseUrl.isNotBlank() &&
            when (form.authMode) {
                AuthMode.TOKEN -> form.apiToken.isNotBlank()
                AuthMode.LOGIN -> form.login.isNotBlank() && form.password.isNotBlank()
            }
    } ?: (form.name.isNotBlank() && form.baseUrl.isNotBlank() &&
        when (form.authMode) {
            AuthMode.TOKEN -> form.apiToken.isNotBlank()
            AuthMode.LOGIN -> form.login.isNotBlank() && form.password.isNotBlank()
        })

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRePinConfirm by remember { mutableStateOf(false) }

    if (pinMismatchDialog != null) {
        PinMismatchDialog(
            state = pinMismatchDialog,
            onConfirm = onConfirmRePin,
            onDismiss = onDismissPinMismatch,
            onVerifiedChange = onRePinVerifiedChange,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.panel_delete_title, form.name)) },
            text = { Text(stringResource(R.string.panel_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeletePanel()
                }) {
                    Text(
                        text = stringResource(R.string.panel_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.panel_delete_cancel))
                }
            },
        )
    }

    if (showRePinConfirm) {
        AlertDialog(
            onDismissRequest = { showRePinConfirm = false },
            title = { Text(stringResource(R.string.panel_tls_repin_label)) },
            text = { Text(stringResource(R.string.panel_tls_pinned_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showRePinConfirm = false
                    onRequestRePin()
                }) {
                    Text(stringResource(R.string.panel_pin_mismatch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRePinConfirm = false }) {
                    Text(stringResource(R.string.panel_pin_mismatch_cancel))
                }
            },
        )
    }

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
                    TextButton(onClick = onNavigateUp) {
                        Text(
                            text = stringResource(R.string.panels_dialog_cancel),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSubmit,
                        enabled = !isSaving && isFormValid,
                    ) {
                        Text(
                            text = stringResource(R.string.panel_action_save),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (!isSaving && isFormValid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
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
        ) {
            // ── Name + URL group ─────────────────────────────────────────────
            GroupCard {
                FieldRow(
                    label = stringResource(R.string.panel_field_name),
                    value = form.name,
                    onValueChange = onNameChange,
                    placeholder = "Stockholm Edge",
                    isError = errors?.name != null,
                    enabled = !isSaving,
                    topDivider = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                FieldRow(
                    label = stringResource(R.string.panel_field_url_label),
                    value = form.baseUrl,
                    onValueChange = onBaseUrlChange,
                    placeholder = "https://panel.example.com:2053",
                    isError = errors?.baseUrl != null,
                    enabled = !isSaving,
                    topDivider = true,
                    monoValue = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            if (errors?.name != null || errors?.baseUrl != null) {
                Spacer(Modifier.height(4.dp))
                if (errors.name != null) {
                    Text(
                        text = stringResource(R.string.panel_error_name_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (errors.baseUrl != null) {
                    Text(
                        text = stringResource(R.string.panel_error_base_url_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Credentials group ────────────────────────────────────────────
            // Login + password are always required (used for cookie-session calls like
            // /panel/setting/defaultSettings, which Bearer auth can't reach). API token
            // is optional — when provided, the app uses Bearer for /panel/api/* to skip
            // the CSRF round-trip.
            GroupCard(title = stringResource(R.string.panel_credentials_section_title)) {
                FieldRow(
                    label = stringResource(R.string.panel_field_login_label),
                    value = form.login,
                    onValueChange = onLoginChange,
                    placeholder = "admin",
                    isError = errors?.login != null,
                    enabled = !isSaving,
                    monoValue = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                PasswordFieldRow(
                    value = form.password,
                    onValueChange = onPasswordChange,
                    isError = errors?.password != null,
                    enabled = !isSaving,
                )
                if (form.twoFactorRequired) {
                    FieldRow(
                        label = stringResource(R.string.panel_field_otp_label),
                        value = form.twoFactorCode,
                        onValueChange = onTwoFactorCodeChange,
                        placeholder = stringResource(R.string.panel_field_otp_placeholder),
                        isError = false,
                        enabled = !isSaving,
                        topDivider = true,
                        monoValue = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            }

            if (form.twoFactorRequired) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.panel_two_factor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (errors?.login != null || errors?.password != null) {
                Spacer(Modifier.height(4.dp))
                if (errors.login != null) {
                    Text(
                        text = stringResource(R.string.panel_error_login_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (errors.password != null) {
                    Text(
                        text = stringResource(R.string.panel_error_password_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── TLS group ─────────────────────────────────────────────────────
            GroupCard(
                title = stringResource(R.string.panel_tls_section_title),
                footer = stringResource(R.string.panel_tls_footer),
            ) {
                // SegmentedPicker row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    SegmentedPicker(
                        options = TlsMode.entries,
                        selected = form.tlsMode,
                        onSelect = onTlsModeChange,
                        label = { mode -> mode.toLabel() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Pinned at row — shown only if pinnedAt is present
                val pinnedAtText = form.pinnedAt?.let { PINNED_AT_FORMATTER.format(it) }
                if (pinnedAtText != null) {
                    GroupRow(
                        label = stringResource(R.string.panel_tls_pinned_at_label),
                        value = pinnedAtText,
                        topDivider = true,
                        showChevron = false,
                    )
                }

                // Re-pin row — only in edit mode with PINNED tls
                if (isEditMode && form.tlsMode == TlsMode.PINNED) {
                    GroupRow(
                        label = stringResource(R.string.panel_tls_repin_label),
                        sub = stringResource(R.string.panel_tls_repin_sub),
                        topDivider = true,
                        showChevron = true,
                        onClick = { showRePinConfirm = true },
                    )
                }
            }

            submitError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.toSubmitErrorMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // ── Delete panel ──────────────────────────────────────────────────
            if (isEditMode) {
                Spacer(Modifier.height(24.dp))
                GroupCard {
                    GroupRow(
                        label = stringResource(R.string.panel_delete_action),
                        danger = true,
                        showChevron = false,
                        topDivider = false,
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


private val FIELD_LABEL_WIDTH = 96.dp
private const val FIELD_ROW_MIN_HEIGHT_DP = 44

@Composable
private fun PasswordFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        fontFamily = MonoFontFamily,
        color = textColor,
    )

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
                .defaultMinSize(minHeight = FIELD_ROW_MIN_HEIGHT_DP.dp)
                .padding(start = 16.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(FIELD_LABEL_WIDTH)) {
                Text(
                    text = stringResource(R.string.panel_field_password_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = textStyle,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = "••••••••",
                                style = textStyle.copy(color = placeholderColor),
                            )
                        }
                        innerTextField()
                    },
                )
            }
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) {
                        stringResource(R.string.panel_cd_hide_password)
                    } else {
                        stringResource(R.string.panel_cd_show_password)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Auth + TLS helpers ────────────────────────────────────────────────────────

@Composable
private fun AuthMode.toLabel(): String = when (this) {
    AuthMode.LOGIN -> stringResource(R.string.panel_auth_mode_login)
    AuthMode.TOKEN -> stringResource(R.string.panel_auth_mode_token)
}

@Composable
private fun TlsMode.toLabel(): String = when (this) {
    TlsMode.SYSTEM -> stringResource(R.string.panel_tls_mode_system)
    TlsMode.PINNED -> stringResource(R.string.panel_tls_mode_pinned_short)
}

// ── PinMismatchDialog (unchanged logic) ───────────────────────────────────────

@Composable
private fun PinMismatchDialog(
    state: PinMismatchDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onVerifiedChange: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panel_pin_mismatch_title)) },
        text = {
            Column {
                Text(stringResource(R.string.panel_pin_mismatch_panel_name, state.panelName))
                Text(state.baseUrl, style = MaterialTheme.typography.bodySmall)
                if (state.pinnedAtFormatted.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.panel_pin_mismatch_pinned_at, state.pinnedAtFormatted))
                }
                if (state.existingSpkiFingerprint.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.panel_pin_mismatch_existing_fp, state.existingSpkiFingerprint))
                }
                if (state.observedSpkiFingerprint.isNotEmpty()) {
                    Text(stringResource(R.string.panel_pin_mismatch_observed_fp, state.observedSpkiFingerprint))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.verifiedByUser,
                        onCheckedChange = onVerifiedChange,
                    )
                    Text(
                        text = stringResource(R.string.panel_pin_mismatch_verified_checkbox),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.verifiedByUser,
            ) {
                Text(stringResource(R.string.panel_pin_mismatch_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.panel_pin_mismatch_cancel))
            }
        },
    )
}

// ── Error mapping ─────────────────────────────────────────────────────────────

@Composable
private fun DomainError.toSubmitErrorMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.error_tls, message)
    is DomainError.Network -> {
        // Include the underlying exception class + message so probe failures stop
        // looking like a generic «no connection» — useful when the panel is
        // reachable but the request itself fails (CSRF, unexpected redirect, …).
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        stringResource(R.string.error_network) + " · " + detail
    }
    is DomainError.PanelUnreachable -> stringResource(R.string.error_panel_unreachable) +
        (httpCode?.let { " · HTTP $it" } ?: "")
    is DomainError.PanelResponse -> stringResource(R.string.error_unexpected) +
        if (body.isNotBlank()) " · $body" else ""
    is DomainError.Unexpected ->
        stringResource(R.string.error_unexpected) + " · " +
            (cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName)
    is DomainError.PinMismatch -> stringResource(R.string.error_pin_mismatch)
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PanelAddEditContentPreview() {
    XuiPanelTheme {
        PanelAddEditContent(
            uiState = PanelAddEditUiState.Editing(
                form = PanelFormState(
                    name = "Stockholm Edge",
                    baseUrl = "https://panel.northwind.io:2053",
                    login = "admin",
                    password = "secret",
                    tlsMode = TlsMode.PINNED,
                ),
                errors = PanelFormErrors(),
                isEditMode = true,
            ),
            onNavigateUp = {},
            onNameChange = {},
            onBaseUrlChange = {},
            onLoginChange = {},
            onPasswordChange = {},
            onTlsModeChange = {},
            onAuthModeChange = {},
            onApiTokenChange = {},
            onTwoFactorCodeChange = {},
            onSubmit = {},
            onConfirmRePin = {},
            onDismissPinMismatch = {},
            onRePinVerifiedChange = {},
            onRequestRePin = {},
            onDeletePanel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PanelAddContentPreview() {
    XuiPanelTheme {
        PanelAddEditContent(
            uiState = PanelAddEditUiState.Editing(
                form = PanelFormState(),
                errors = PanelFormErrors(),
                isEditMode = false,
            ),
            onNavigateUp = {},
            onNameChange = {},
            onBaseUrlChange = {},
            onLoginChange = {},
            onPasswordChange = {},
            onTlsModeChange = {},
            onAuthModeChange = {},
            onApiTokenChange = {},
            onTwoFactorCodeChange = {},
            onSubmit = {},
            onConfirmRePin = {},
            onDismissPinMismatch = {},
            onRePinVerifiedChange = {},
            onRequestRePin = {},
            onDeletePanel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PanelAddContentTwoFactorPreview() {
    XuiPanelTheme {
        PanelAddEditContent(
            uiState = PanelAddEditUiState.Editing(
                form = PanelFormState(
                    name = "Stockholm Edge",
                    baseUrl = "https://panel.northwind.io:2053",
                    login = "admin",
                    password = "secret",
                    twoFactorRequired = true,
                ),
                errors = PanelFormErrors(),
                isEditMode = false,
            ),
            onNavigateUp = {},
            onNameChange = {},
            onBaseUrlChange = {},
            onLoginChange = {},
            onPasswordChange = {},
            onTlsModeChange = {},
            onAuthModeChange = {},
            onApiTokenChange = {},
            onTwoFactorCodeChange = {},
            onSubmit = {},
            onConfirmRePin = {},
            onDismissPinMismatch = {},
            onRePinVerifiedChange = {},
            onRequestRePin = {},
            onDeletePanel = {},
        )
    }
}
