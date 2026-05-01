package com.firesin.xuipanel.feature.clients

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme

@Composable
fun ClientDeleteConfirmDialog(
    clientEmail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.client_delete_title, clientEmail))
        },
        text = {
            Text(stringResource(R.string.client_delete_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.client_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.client_delete_cancel))
            }
        },
    )
}

@Composable
fun ClientResetConfirmDialog(
    clientEmail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.client_reset_title, clientEmail))
        },
        text = {
            Text(stringResource(R.string.client_reset_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.client_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.client_reset_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun ClientDeleteConfirmDialogPreview() {
    XuiPanelTheme {
        ClientDeleteConfirmDialog(
            clientEmail = "user@example.com",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ClientResetConfirmDialogPreview() {
    XuiPanelTheme {
        ClientResetConfirmDialog(
            clientEmail = "user@example.com",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
