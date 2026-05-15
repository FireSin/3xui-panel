package com.firesin.xuipanel.feature.settings.warpnord.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.warpnord.WarpNordScreen

const val WarpNordRoute = "warp_nord"

fun NavController.navigateToWarpNord() = navigate(WarpNordRoute)

fun NavGraphBuilder.warpNordGraph(onBack: () -> Unit) {
    composable(route = WarpNordRoute) {
        WarpNordScreen(onBack = onBack)
    }
}
