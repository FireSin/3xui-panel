package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme

private val ERROR_ICON_CIRCLE_SIZE = 96.dp
private val ERROR_ICON_SIZE = 40.dp
private val ERROR_MAX_DESCRIPTION_WIDTH = 280.dp

@Composable
fun ErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ERROR_ICON_CIRCLE_SIZE)
                .background(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(ERROR_ICON_SIZE),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = ERROR_MAX_DESCRIPTION_WIDTH),
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    XuiPanelTheme {
        ErrorState(
            title = "Что-то пошло не так",
            description = "Нет соединения с панелью. Проверьте подключение к интернету.",
            actionLabel = "Повторить",
            onAction = {},
        )
    }
}
