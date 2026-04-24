package com.firesin.xuipanel.feature.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme

@Composable
fun ShareScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Share — coming soon")
    }
}

@Preview(showBackground = true)
@Composable
private fun ShareScreenPreview() {
    XuiPanelTheme {
        ShareScreen()
    }
}
