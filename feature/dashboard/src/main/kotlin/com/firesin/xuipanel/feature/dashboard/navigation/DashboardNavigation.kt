package com.firesin.xuipanel.feature.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.dashboard.DashboardScreen

fun NavGraphBuilder.dashboardGraph(navController: NavController) {
    composable(route = "dashboard") {
        DashboardScreen()
    }
}
