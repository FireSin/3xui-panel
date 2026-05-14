package com.firesin.xuipanel.feature.settings.geo.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.geo.GeoSourcesScreen

const val GeoSourcesRoute = "geo_sources"

fun NavController.navigateToGeoSources() = navigate(GeoSourcesRoute)

fun NavGraphBuilder.geoSourcesGraph(
    onBack: () -> Unit,
) {
    composable(route = GeoSourcesRoute) {
        GeoSourcesScreen(onBack = onBack)
    }
}
