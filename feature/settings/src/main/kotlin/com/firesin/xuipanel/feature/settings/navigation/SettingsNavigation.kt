package com.firesin.xuipanel.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.SettingsScreen
import com.firesin.xuipanel.feature.settings.geo.navigation.GeoSourcesRoute
import com.firesin.xuipanel.feature.settings.geo.navigation.geoSourcesGraph

const val SettingsRoute = "settings"

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onMenuClick: () -> Unit = {},
) {
    composable(route = SettingsRoute) {
        SettingsScreen(
            onMenuClick = onMenuClick,
            onNavigateToGeoSources = { navController.navigate(GeoSourcesRoute) },
        )
    }
    geoSourcesGraph(onBack = { navController.popBackStack() })
}
