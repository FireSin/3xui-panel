package com.firesin.xuipanel.feature.panels.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firesin.xuipanel.feature.panels.PanelAddEditScreen
import com.firesin.xuipanel.feature.panels.PanelsListScreen
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditViewModel

// Route tokens used as cross-module navigation targets.
const val PanelsListRoute = "panels_list"
const val PanelAddRoute = "panels_add"
const val PanelEditRoute = "panels_edit/{${PanelAddEditViewModel.ARG_PANEL_ID}}"

// Cross-module destination tokens (no direct feature deps needed).
const val DashboardRoute = "dashboard"
const val InboundsRoute = "inbounds"
const val ClientsRoute = "clients"
const val ShareRoute = "share"
const val StatsRoute = "stats"

// Kept for backward compat with XuiNavHost startDestination.
const val PanelsRoute = PanelsListRoute

fun panelEditRoute(panelId: String) = "panels_edit/${Uri.encode(panelId)}"

fun NavGraphBuilder.panelsGraph(navController: NavController) {
    composable(route = PanelsListRoute) {
        PanelsListScreen(
            onAddPanel = { navController.navigate(PanelAddRoute) },
            onEditPanel = { id -> navController.navigate(panelEditRoute(id)) },
            onPanelSelected = { navController.navigate(DashboardRoute) },
        )
    }

    composable(route = PanelAddRoute) {
        PanelAddEditScreen(
            onSaved = { navController.popBackStack() },
            onNavigateUp = { navController.navigateUp() },
        )
    }

    composable(
        route = PanelEditRoute,
        arguments = listOf(
            navArgument(PanelAddEditViewModel.ARG_PANEL_ID) { type = NavType.StringType },
        ),
    ) {
        PanelAddEditScreen(
            onSaved = { navController.popBackStack() },
            onNavigateUp = { navController.navigateUp() },
        )
    }
}
