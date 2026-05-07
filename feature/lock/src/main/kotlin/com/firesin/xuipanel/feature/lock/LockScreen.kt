package com.firesin.xuipanel.feature.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LockScreen(
    viewModel: LockViewModel = viewModel(),
    onFinishApp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Auto-trigger prompt on first composition
    LaunchedEffect(Unit) {
        if (activity != null) {
            triggerBiometricPrompt(
                activity = activity,
                viewModel = viewModel,
            )
        }
    }

    when (val state = uiState) {
        is LockUiState.SystemBiometryRemoved -> {
            SystemBiometryRemovedDialog(
                onDisableLock = viewModel::disableLockAndAuthenticate,
                onClose = onFinishApp,
            )
        }

        else -> {
            LockScreenContent(
                onUnlockClick = {
                    if (activity != null) {
                        triggerBiometricPrompt(
                            activity = activity,
                            viewModel = viewModel,
                        )
                    }
                },
                errorMessage = (state as? LockUiState.Failed)?.message,
            )
        }
    }
}

@Composable
private fun LockScreenContent(
    onUnlockClick: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlockClick) {
                Text(stringResource(R.string.lock_btn_unlock))
            }
        }
    }
}

@Composable
private fun SystemBiometryRemovedDialog(
    onDisableLock: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.lock_system_biometry_removed_title)) },
        text = { Text(stringResource(R.string.lock_system_biometry_removed_message)) },
        confirmButton = {
            TextButton(onClick = onDisableLock) {
                Text(stringResource(R.string.lock_system_biometry_removed_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.lock_system_biometry_removed_cancel))
            }
        },
    )
}

private fun triggerBiometricPrompt(
    activity: FragmentActivity,
    viewModel: LockViewModel,
) {
    viewModel.onAuthenticating()

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_biometric_prompt_title))
        .setSubtitle(activity.getString(R.string.lock_biometric_prompt_subtitle))
        .setAllowedAuthenticators(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()

    val delegate = BiometricAuthDelegate(
        activity = activity,
        onSuccess = viewModel::onAuthSuccess,
        onError = viewModel::onAuthError,
        onFailed = viewModel::onAuthFailed,
    )
    delegate.authenticate(promptInfo)
}

@Preview(showBackground = true)
@Composable
private fun LockScreenContentPreview() {
    LockScreenContent(
        onUnlockClick = {},
        errorMessage = null,
    )
}
