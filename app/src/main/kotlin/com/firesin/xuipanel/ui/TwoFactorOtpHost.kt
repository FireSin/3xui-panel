package com.firesin.xuipanel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.firesin.xuipanel.R
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpBus
import com.firesin.xuipanel.core.common.twofactor.TwoFactorOtpRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.foundation.text.KeyboardOptions

@HiltViewModel
class TwoFactorOtpHostViewModel @Inject constructor(
    val bus: TwoFactorOtpBus,
) : ViewModel()

@Composable
fun TwoFactorOtpHost(viewModel: TwoFactorOtpHostViewModel = hiltViewModel()) {
    var pending by remember { mutableStateOf<TwoFactorOtpRequest?>(null) }
    LaunchedEffect(Unit) {
        viewModel.bus.requests.collect { pending = it }
    }
    pending?.let { req ->
        var code by remember(req.requestId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                viewModel.bus.completeRequest(req.requestId, null)
                pending = null
            },
            title = { Text(stringResource(R.string.twofa_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.twofa_dialog_body, req.panelName))
                    Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.twofa_dialog_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.bus.completeRequest(req.requestId, code)
                        pending = null
                    },
                    enabled = code.length == 6,
                ) { Text(stringResource(R.string.twofa_dialog_submit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.bus.completeRequest(req.requestId, null)
                    pending = null
                }) { Text(stringResource(R.string.twofa_dialog_cancel)) }
            },
        )
    }
}
