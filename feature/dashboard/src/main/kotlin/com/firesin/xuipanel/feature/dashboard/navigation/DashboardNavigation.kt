package com.firesin.xuipanel.feature.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.dashboard.DashboardScreen

const val DashboardRoute = "dashboard"

fun NavGraphBuilder.dashboardGraph(
    navController: NavController,
    panelsAddRoute: String,
    onNavigateToStats: () -> Unit,
) {
    composable(route = DashboardRoute) {
        DashboardScreen(
            onAddPanel = { navController.navigate(panelsAddRoute) },
            onNavigateToStats = onNavigateToStats,
        )
    }
}
