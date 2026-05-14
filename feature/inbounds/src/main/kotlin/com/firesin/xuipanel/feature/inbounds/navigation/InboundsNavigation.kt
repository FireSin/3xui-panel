package com.firesin.xuipanel.feature.inbounds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.inbounds.InboundsScreen
import com.firesin.xuipanel.feature.inbounds.add.navigation.AddInboundRoute
import com.firesin.xuipanel.feature.inbounds.add.navigation.addInboundGraph
import com.firesin.xuipanel.feature.inbounds.add.navigation.navigateToAddInbound
import com.firesin.xuipanel.feature.inbounds.add.navigation.navigateToEditInbound

const val InboundsRoute = "inbounds"

fun NavController.navigateToManageClients(inboundId: Int) {
    navigate("clients/$inboundId")
}

fun NavGraphBuilder.inboundsGraph(
    navController: NavController,
    panelsAddRoute: String = "panels_add",
    onManageClients: (inboundId: Int) -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    composable(route = InboundsRoute) {
        InboundsScreen(
            onAddPanel = { navController.navigate(panelsAddRoute) },
            onManageClients = onManageClients,
            onMenuClick = onMenuClick,
            onNavigateAddInbound = { navController.navigateToAddInbound() },
            onNavigateEditInbound = { inboundId -> navController.navigateToEditInbound(inboundId) },
        )
    }

    addInboundGraph(
        navController = navController,
        onSaved = {},
    )
}
