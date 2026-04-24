package com.firesin.xuipanel.feature.panels.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.panels.PanelsScreen

// Route tokens used as cross-module navigation targets (no inter-feature deps needed).
const val PanelsRoute = "panels"
const val DashboardRoute = "dashboard"
const val InboundsRoute = "inbounds"
const val ClientsRoute = "clients"
const val ShareRoute = "share"
const val StatsRoute = "stats"

fun NavGraphBuilder.panelsGraph(navController: NavController) {
    composable(route = PanelsRoute) {
        PanelsScreen(navController = navController)
    }
}
