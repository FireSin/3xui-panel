package com.firesin.xuipanel.feature.settings.outbounds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.outbounds.OutboundsScreen

const val OutboundsRoute = "outbounds"

fun NavController.navigateToOutbounds() = navigate(OutboundsRoute)

fun NavGraphBuilder.outboundsGraph(onBack: () -> Unit) {
    composable(route = OutboundsRoute) {
        OutboundsScreen(onBack = onBack)
    }
}
