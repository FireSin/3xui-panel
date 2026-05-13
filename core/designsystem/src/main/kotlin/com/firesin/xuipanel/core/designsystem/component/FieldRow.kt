package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily

private const val FIELD_ROW_MIN_HEIGHT_DP = 44

/**
 * A single labelled input row used inside [GroupCard].
 *
 * Layout: Box(width=[labelWidth]) label | Box(weight=1) BasicTextField | optional [trailing].
 * A 0.5 dp hairline divider is prepended when [topDivider]=true.
 */
@Composable
fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    monoValue: Boolean = false,
    placeholder: String? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    labelWidth: Dp = 96.dp,
    topDivider: Boolean = true,
    stacked: Boolean = false,
    singleLineValue: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        val textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            fontFamily = if (monoValue) MonoFontFamily else null,
            color = textColor,
        )
        val labelColor = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface

        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = labelColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (trailing != null) trailing()
                }
                Spacer(Modifier.height(2.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    enabled = enabled,
                    singleLine = singleLineValue,
                    textStyle = textStyle,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = placeholderColor),
                            )
                        }
                        innerTextField()
                    },
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = FIELD_ROW_MIN_HEIGHT_DP.dp)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(labelWidth)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = labelColor,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        enabled = enabled,
                        singleLine = singleLineValue,
                        textStyle = textStyle,
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textStyle.copy(color = placeholderColor),
                                )
                            }
                            innerTextField()
                        },
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(4.dp))
                    trailing()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FieldRowPreview() {
    GroupCard {
        FieldRow(
            label = "Email",
            value = "alice@studio",
            onValueChange = {},
            topDivider = false,
        )
        FieldRow(
            label = "UUID",
            value = "3f7b8c2e-9a14-4d1a-b6f3-2e5d1c0a9b7e",
            onValueChange = {},
            monoValue = true,
        )
    }
}
