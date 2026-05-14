package com.firesin.xuipanel.feature.nodes

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.core.xui.dto.NodeStatusProbeDto
import com.firesin.xuipanel.feature.nodes.ui.NodeFormUiState
import com.firesin.xuipanel.feature.nodes.ui.NodeFormViewModel
import com.firesin.xuipanel.feature.nodes.ui.isValid

@Composable
fun NodeFormScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: NodeFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is NodeFormUiState.Saved) {
            onNavigateBack()
        }
    }

    val editing = uiState as? NodeFormUiState.Editing
    val errorMessage = editing?.errorMessage
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    NodeFormContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onUpdateName = viewModel::updateName,
        onUpdateRemark = viewModel::updateRemark,
        onUpdateScheme = viewModel::updateScheme,
        onUpdateAddress = viewModel::updateAddress,
        onUpdatePort = viewModel::updatePort,
        onUpdateBasePath = viewModel::updateBasePath,
        onUpdateApiToken = viewModel::updateApiToken,
        onUpdateAllowPrivate = viewModel::updateAllowPrivateAddress,
        onUpdateEnable = viewModel::updateEnable,
        onTestConnection = viewModel::testConnection,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeFormContent(
    uiState: NodeFormUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit = {},
    onUpdateName: (String) -> Unit = {},
    onUpdateRemark: (String) -> Unit = {},
    onUpdateScheme: (String) -> Unit = {},
    onUpdateAddress: (String) -> Unit = {},
    onUpdatePort: (String) -> Unit = {},
    onUpdateBasePath: (String) -> Unit = {},
    onUpdateApiToken: (String) -> Unit = {},
    onUpdateAllowPrivate: (Boolean) -> Unit = {},
    onUpdateEnable: (Boolean) -> Unit = {},
    onTestConnection: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val editing = uiState as? NodeFormUiState.Editing
    val isEditMode = editing?.editingNodeId != null
    val isSaving = editing?.isSaving == true
    val isTesting = editing?.isTesting == true
    val fields = editing?.fields

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) stringResource(R.string.nodes_form_title_edit)
                        else stringResource(R.string.nodes_form_title_add),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nodes_form_cancel),
                        )
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    } else {
                        IconButton(
                            onClick = onSave,
                            enabled = fields?.isValid == true,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = if (isEditMode) stringResource(R.string.nodes_form_save)
                                else stringResource(R.string.nodes_form_create),
                                tint = if (fields?.isValid == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (editing?.isLoading == true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Name
            OutlinedTextField(
                value = fields?.name.orEmpty(),
                onValueChange = onUpdateName,
                label = { Text(stringResource(R.string.nodes_form_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Remark
            OutlinedTextField(
                value = fields?.remark.orEmpty(),
                onValueChange = onUpdateRemark,
                label = { Text(stringResource(R.string.nodes_form_field_remark)) },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Scheme segmented picker
            Text(
                text = stringResource(R.string.nodes_form_field_scheme),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SegmentedPicker(
                options = listOf("http", "https"),
                selected = fields?.scheme ?: "https",
                onSelect = onUpdateScheme,
                label = { it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Address
            OutlinedTextField(
                value = fields?.address.orEmpty(),
                onValueChange = onUpdateAddress,
                label = { Text(stringResource(R.string.nodes_form_field_address)) },
                placeholder = { Text("1.2.3.4 или panel.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Port
            OutlinedTextField(
                value = fields?.port.orEmpty(),
                onValueChange = onUpdatePort,
                label = { Text(stringResource(R.string.nodes_form_field_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Base path
            OutlinedTextField(
                value = fields?.basePath.orEmpty(),
                onValueChange = onUpdateBasePath,
                label = { Text(stringResource(R.string.nodes_form_field_base_path)) },
                placeholder = { Text("/secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // API token
            OutlinedTextField(
                value = fields?.apiToken.orEmpty(),
                onValueChange = onUpdateApiToken,
                label = { Text(stringResource(R.string.nodes_form_field_api_token)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Allow private address toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nodes_form_field_allow_private),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = fields?.allowPrivateAddress == true,
                    onCheckedChange = onUpdateAllowPrivate,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nodes_form_field_enable),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = fields?.enable != false,
                    onCheckedChange = onUpdateEnable,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Test connection button
            OutlinedButton(
                onClick = onTestConnection,
                enabled = fields?.isValid == true && !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.nodes_form_test_button))
            }

            // Test result
            val testResult = editing?.testResult
            if (testResult != null) {
                Spacer(Modifier.height(8.dp))
                when (testResult) {
                    is NodeFormUiState.TestResult.Success -> {
                        val probe = testResult.probe
                        Text(
                            text = stringResource(
                                R.string.nodes_form_test_success,
                                probe.latencyMs,
                                probe.xrayVersion.ifBlank { "?" },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is NodeFormUiState.TestResult.Failure -> {
                        Text(
                            text = stringResource(R.string.nodes_form_test_failure, testResult.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save/Create button
            Button(
                onClick = onSave,
                enabled = fields?.isValid == true && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isEditMode) stringResource(R.string.nodes_form_save)
                    else stringResource(R.string.nodes_form_create),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeFormScreenAddPreview() {
    XuiPanelTheme {
        NodeFormContent(
            uiState = NodeFormUiState.Editing(),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeFormScreenTestSuccessPreview() {
    XuiPanelTheme {
        NodeFormContent(
            uiState = NodeFormUiState.Editing(
                testResult = NodeFormUiState.TestResult.Success(
                    NodeStatusProbeDto(status = "online", latencyMs = 45, xrayVersion = "25.4.0"),
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}
