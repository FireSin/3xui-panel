package com.firesin.xuipanel.feature.inbounds.add.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.addExpiry
import com.firesin.xuipanel.core.common.util.addExpiryMonths
import com.firesin.xuipanel.core.common.util.formatRemainingTime
import com.firesin.xuipanel.core.designsystem.component.FieldRow
import com.firesin.xuipanel.core.designsystem.component.GroupCard
import com.firesin.xuipanel.core.designsystem.component.IosToggle
import com.firesin.xuipanel.core.designsystem.component.SectionHeader
import com.firesin.xuipanel.feature.inbounds.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommonSection(
    remark: String,
    port: String,
    listen: String,
    enable: Boolean,
    expiryTime: Long,
    totalGb: String,
    onRemarkChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onListenChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onExpiryTimeChange: (Long) -> Unit,
    onTotalGbChange: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.add_inbound_section_common))
    GroupCard {
        FieldRow(
            label = stringResource(R.string.add_inbound_field_remark),
            value = remark,
            onValueChange = onRemarkChange,
            topDivider = false,
            stacked = true,
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_port),
            value = port,
            onValueChange = onPortChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_listen),
            value = listen,
            onValueChange = onListenChange,
            placeholder = "0.0.0.0",
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_total_gb),
            value = totalGb,
            onValueChange = onTotalGbChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ExpiryFieldRow(
            expiryTime = expiryTime,
            onPickDate = { showDatePicker = true },
            onClear = { onExpiryTimeChange(0L) },
        )
        FieldRow(
            label = stringResource(R.string.add_inbound_field_enable),
            value = "",
            onValueChange = {},
            readOnly = true,
            trailing = {
                IosToggle(checked = enable, onCheckedChange = onEnableChange)
            },
        )
    }

    ExpiryChipsRow(expiryTime = expiryTime, onSetExpiryTime = onExpiryTimeChange)

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (expiryTime > 0L) expiryTime else null,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onExpiryTimeChange(datePickerState.selectedDateMillis ?: 0L)
                    showDatePicker = false
                }) { Text(stringResource(R.string.add_inbound_expiry_pick)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.add_inbound_cancel))
                }
            },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun ExpiryFieldRow(expiryTime: Long, onPickDate: () -> Unit, onClear: () -> Unit) {
    val remaining = formatRemainingTime(expiryTime)
    val label = if (expiryTime > 0L) {
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expiryTime))
        if (remaining != null) "$dateStr ($remaining)" else dateStr
    } else {
        stringResource(R.string.add_inbound_expiry_no_limit)
    }
    FieldRow(
        label = stringResource(R.string.add_inbound_field_expiry),
        value = label,
        onValueChange = {},
        readOnly = true,
        trailing = {
            if (expiryTime > 0L) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.add_inbound_expiry_clear), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                TextButton(onClick = onPickDate) {
                    Text(stringResource(R.string.add_inbound_expiry_pick), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpiryChipsRow(expiryTime: Long, onSetExpiryTime: (Long) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(7, 14, 30).forEach { days ->
            AssistChip(
                onClick = { onSetExpiryTime(addExpiry(expiryTime, days * MS_PER_DAY)) },
                label = { Text("+${days}д") },
            )
        }
        listOf(1 to "+1 мес", 3 to "+3 мес", 6 to "+6 мес").forEach { (months, label) ->
            AssistChip(
                onClick = { onSetExpiryTime(addExpiryMonths(expiryTime, months)) },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = expiryTime == 0L,
            onClick = { onSetExpiryTime(0L) },
            label = { Text(stringResource(R.string.add_inbound_expiry_no_limit)) },
        )
    }
    Spacer(Modifier.height(4.dp))
}
