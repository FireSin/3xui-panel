package com.firesin.xuipanel.feature.inbounds

import androidx.compose.runtime.Composable

/**
 * Entry-point composable kept for backward compat with nav graph.
 * All logic lives in [InboundsListScreen].
 */
@Composable
fun InboundsScreen(
    onAddPanel: () -> Unit = {},
    onManageClients: (inboundId: Int) -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    InboundsListScreen(
        onAddPanel = onAddPanel,
        onManageClients = onManageClients,
        onMenuClick = onMenuClick,
    )
}
