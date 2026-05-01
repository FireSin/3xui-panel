package com.firesin.xuipanel.feature.clients

import androidx.compose.runtime.Composable

/**
 * Entry-point composable kept for backward compat with nav graph.
 * All logic lives in [ClientsListScreen].
 */
@Composable
fun ClientsScreen(
    initialInboundId: Int? = null,
    onAddPanel: () -> Unit = {},
    onNavigateAdd: (inboundId: Int) -> Unit = {},
    onNavigateEdit: (inboundId: Int, clientKey: String) -> Unit = { _, _ -> },
) {
    ClientsListScreen(
        initialInboundId = initialInboundId,
        onAddPanel = onAddPanel,
        onNavigateAdd = onNavigateAdd,
        onNavigateEdit = onNavigateEdit,
    )
}
