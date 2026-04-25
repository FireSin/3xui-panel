package com.firesin.xuipanel.feature.inbounds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.inbounds.InboundsScreen

const val InboundsRoute = "inbounds"

fun NavGraphBuilder.inboundsGraph(
    navController: NavController,
    panelsAddRoute: String = "panels_add",
) {
    composable(route = InboundsRoute) {
        InboundsScreen(
            onAddPanel = { navController.navigate(panelsAddRoute) },
        )
    }
}
