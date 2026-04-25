package com.firesin.xuipanel.feature.panels

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditUiState
import com.firesin.xuipanel.feature.panels.ui.PanelFormErrors
import com.firesin.xuipanel.feature.panels.ui.PanelFormState
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditViewModel

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
        onTrustSelfSignedChange = viewModel::updateTrustSelfSigned,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelAddEditContent(
    uiState: PanelAddEditUiState,
    onNavigateUp: () -> Unit,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTrustSelfSignedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
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

    val title = if (isEditMode) {
        stringResource(R.string.panel_edit_title)
    } else {
        stringResource(R.string.panel_add_title)
    }

    val isFormValid = errors?.let { e ->
        e.name == null && e.baseUrl == null && e.login == null && e.password == null &&
            form.name.isNotBlank() && form.baseUrl.isNotBlank() &&
            form.login.isNotBlank() && form.password.isNotBlank()
    } ?: (form.name.isNotBlank() && form.baseUrl.isNotBlank() &&
        form.login.isNotBlank() && form.password.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.panel_field_name)) },
                isError = errors?.name != null,
                supportingText = if (errors?.name != null) {
                    { Text(stringResource(R.string.panel_error_name_empty)) }
                } else null,
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text(stringResource(R.string.panel_field_base_url)) },
                isError = errors?.baseUrl != null,
                supportingText = if (errors?.baseUrl != null) {
                    { Text(stringResource(R.string.panel_error_base_url_invalid)) }
                } else null,
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.login,
                onValueChange = onLoginChange,
                label = { Text(stringResource(R.string.panel_field_login)) },
                isError = errors?.login != null,
                supportingText = if (errors?.login != null) {
                    { Text(stringResource(R.string.panel_error_login_empty)) }
                } else null,
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            PasswordField(
                value = form.password,
                onValueChange = onPasswordChange,
                isError = errors?.password != null,
                enabled = !isSaving,
            )

            Spacer(Modifier.height(16.dp))

            TrustSelfSignedRow(
                checked = form.trustSelfSigned,
                onCheckedChange = onTrustSelfSignedChange,
                enabled = !isSaving,
            )

            if (form.trustSelfSigned) {
                Spacer(Modifier.height(8.dp))
                TrustSelfSignedWarning()
            }

            submitError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.toSubmitErrorMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !isSaving && isFormValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(stringResource(R.string.panel_action_save))
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.panel_field_password)) },
        isError = isError,
        supportingText = if (isError) {
            { Text(stringResource(R.string.panel_error_password_empty)) }
        } else null,
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) {
                        stringResource(R.string.panel_cd_hide_password)
                    } else {
                        stringResource(R.string.panel_cd_show_password)
                    },
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrustSelfSignedRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.panel_switch_trust_self_signed),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun TrustSelfSignedWarning(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 8.dp, top = 2.dp),
            )
            Text(
                text = stringResource(R.string.panel_warning_trust_self_signed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DomainError.toSubmitErrorMessage(): String = when (this) {
    is DomainError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    is DomainError.Tls -> stringResource(R.string.error_tls, message)
    is DomainError.Network -> stringResource(R.string.error_network)
    is DomainError.PanelUnreachable -> stringResource(R.string.error_panel_unreachable)
    is DomainError.PanelResponse -> stringResource(R.string.error_unexpected)
    is DomainError.Unexpected -> stringResource(R.string.error_unexpected)
}

@Preview(showBackground = true)
@Composable
private fun PanelAddEditContentPreview() {
    XuiPanelTheme {
        PanelAddEditContent(
            uiState = PanelAddEditUiState.Editing(
                form = PanelFormState(
                    name = "Мой сервер",
                    baseUrl = "https://panel.example.com:2053",
                    login = "admin",
                    password = "",
                    trustSelfSigned = true,
                ),
                errors = PanelFormErrors(password = ""),
            ),
            onNavigateUp = {},
            onNameChange = {},
            onBaseUrlChange = {},
            onLoginChange = {},
            onPasswordChange = {},
            onTrustSelfSignedChange = {},
            onSubmit = {},
        )
    }
}
