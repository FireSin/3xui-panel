package com.firesin.xuipanel.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.firesin.xuipanel.feature.clients.navigation.clientsGraph
import com.firesin.xuipanel.feature.dashboard.navigation.dashboardGraph
import com.firesin.xuipanel.feature.inbounds.navigation.inboundsGraph
import com.firesin.xuipanel.feature.inbounds.navigation.navigateToManageClients
import com.firesin.xuipanel.feature.panels.navigation.PanelAddRoute
import com.firesin.xuipanel.feature.panels.navigation.PanelsRoute
import com.firesin.xuipanel.feature.panels.navigation.panelsGraph
import com.firesin.xuipanel.feature.share.navigation.navigateToShare
import com.firesin.xuipanel.feature.share.navigation.shareGraph
import com.firesin.xuipanel.feature.stats.navigation.statsGraph

@Composable
fun XuiNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = PanelsRoute,
    ) {
        panelsGraph(navController)
        dashboardGraph(navController, panelsAddRoute = PanelAddRoute)
        inboundsGraph(
            navController = navController,
            onManageClients = { inboundId -> navController.navigateToManageClients(inboundId) },
        )
        clientsGraph(
            navController = navController,
            panelsAddRoute = PanelAddRoute,
            onNavigateShare = { inboundId, clientKey ->
                navController.navigateToShare(inboundId, clientKey)
            },
        )
        shareGraph(navController)
        statsGraph(navController)
    }
}
