package com.firesin.xuipanel.feature.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.dashboard.DashboardScreen
import com.firesin.xuipanel.feature.dashboard.LogsScreen

const val DashboardRoute = "dashboard"
const val LogsRoute = "logs"

fun NavGraphBuilder.dashboardGraph(
    navController: NavController,
    panelsAddRoute: String,
    onNavigateToStats: () -> Unit,
    onNavigateToInbounds: () -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    composable(route = DashboardRoute) {
        DashboardScreen(
            onAddPanel = { navController.navigate(panelsAddRoute) },
            onNavigateToStats = onNavigateToStats,
            onNavigateToInbounds = onNavigateToInbounds,
            onMenuClick = onMenuClick,
            onNavigateToLogs = { navController.navigate(LogsRoute) },
        )
    }
    composable(route = LogsRoute) {
        LogsScreen(onPopBackStack = { navController.popBackStack() })
    }
}
