package com.firesin.xuipanel.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.firesin.xuipanel.ui.WsToastHost
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.firesin.xuipanel.feature.clients.navigation.clientsGraph
import com.firesin.xuipanel.feature.dashboard.navigation.dashboardGraph
import com.firesin.xuipanel.feature.inbounds.navigation.InboundsRoute
import com.firesin.xuipanel.feature.inbounds.navigation.inboundsGraph
import com.firesin.xuipanel.feature.inbounds.navigation.navigateToManageClients
import com.firesin.xuipanel.feature.nodes.navigation.nodesGraph
import com.firesin.xuipanel.feature.panels.navigation.PanelAddRoute
import com.firesin.xuipanel.feature.panels.navigation.PanelsRoute
import com.firesin.xuipanel.feature.panels.navigation.panelsGraph
import com.firesin.xuipanel.feature.settings.navigation.SettingsRoute
import com.firesin.xuipanel.feature.settings.navigation.settingsGraph
import com.firesin.xuipanel.feature.share.navigation.navigateToShare
import com.firesin.xuipanel.feature.share.navigation.shareGraph
import com.firesin.xuipanel.feature.stats.navigation.clientStatsGraph
import com.firesin.xuipanel.feature.stats.navigation.navigateToClientStats
import com.firesin.xuipanel.feature.stats.navigation.statsGraph

@Composable
fun XuiNavHost(
    navController: NavHostController = rememberNavController(),
    @Suppress("UNUSED_PARAMETER") installId: String = "",
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in XuiTopLevelRoutes
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        // Top/side insets are consumed by each screen's own Scaffold (TopAppBar with
        // status-bar inset). The outer Scaffold only owns the bottom-bar slot, so we
        // disable its window-inset contribution to avoid stacking a second status-bar
        // gap above every screen's title.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { WsToastHost(snackbarHostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                XuiBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PanelsRoute,
            modifier = Modifier.padding(padding),
        ) {
            panelsGraph(
                navController = navController,
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
            )
            dashboardGraph(
                navController = navController,
                panelsAddRoute = PanelAddRoute,
                onNavigateToStats = { navController.navigate("stats") },
                onNavigateToInbounds = { navController.navigate(InboundsRoute) },
            )
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
            clientStatsGraph(navController)
            settingsGraph(navController = navController, panelsAddRoute = PanelAddRoute)
            nodesGraph(navController = navController)
        }
    }
}
