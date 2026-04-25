package com.firesin.xuipanel.feature.inbounds

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme

@Composable
fun InboundDeleteConfirmDialog(
    inboundName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.inbound_delete_title, inboundName))
        },
        text = {
            Text(stringResource(R.string.inbound_delete_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.inbound_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.inbound_delete_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun InboundDeleteConfirmDialogPreview() {
    XuiPanelTheme {
        InboundDeleteConfirmDialog(
            inboundName = "VMess-443",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
