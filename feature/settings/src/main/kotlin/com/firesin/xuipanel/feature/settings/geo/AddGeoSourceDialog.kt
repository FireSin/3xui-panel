package com.firesin.xuipanel.feature.settings.geo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.designsystem.component.SegmentedPicker
import com.firesin.xuipanel.core.xui.dto.CustomGeoResourceDto
import com.firesin.xuipanel.feature.settings.R

private const val TYPE_GEOIP = "geoip"
private const val TYPE_GEOSITE = "geosite"
private val GEO_TYPES = listOf(TYPE_GEOIP, TYPE_GEOSITE)

@Composable
fun AddGeoSourceDialog(
    initial: CustomGeoResourceDto? = null,
    onDismiss: () -> Unit,
    onSave: (type: String, alias: String, url: String) -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: TYPE_GEOIP) }
    var alias by remember { mutableStateOf(initial?.alias.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }

    val isUrlValid = url.startsWith("http://") || url.startsWith("https://")
    val canSave = alias.isNotBlank() && url.isNotBlank() && isUrlValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) {
                    stringResource(R.string.geo_sources_dialog_title_add)
                } else {
                    stringResource(R.string.geo_sources_dialog_title_edit)
                },
            )
        },
        text = {
            Column {
                Text(text = stringResource(R.string.geo_sources_field_type))
                Spacer(Modifier.height(6.dp))
                SegmentedPicker(
                    options = GEO_TYPES,
                    selected = type,
                    onSelect = { type = it },
                    label = { t ->
                        if (t == TYPE_GEOIP) {
                            stringResource(R.string.geo_sources_type_geoip)
                        } else {
                            stringResource(R.string.geo_sources_type_geosite)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.geo_sources_field_alias)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.geo_sources_field_url)) },
                    singleLine = true,
                    isError = url.isNotBlank() && !isUrlValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(type, alias.trim(), url.trim()) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.geo_sources_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.geo_sources_dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AddGeoSourceDialogPreview() {
    AddGeoSourceDialog(
        initial = null,
        onDismiss = {},
        onSave = { _, _, _ -> },
    )
}

@Preview(showBackground = true)
@Composable
private fun EditGeoSourceDialogPreview() {
    AddGeoSourceDialog(
        initial = CustomGeoResourceDto(
            id = 1,
            type = TYPE_GEOSITE,
            alias = "mylist",
            url = "https://example.com/list.dat",
        ),
        onDismiss = {},
        onSave = { _, _, _ -> },
    )
}
