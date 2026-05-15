package com.firesin.xuipanel.feature.settings.panelsetup.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.panelsetup.PanelSetupScreen

const val PanelSetupRoute = "panel_setup"

fun NavController.navigateToPanelSetup() = navigate(PanelSetupRoute)

fun NavGraphBuilder.panelSetupGraph(onBack: () -> Unit) {
    composable(route = PanelSetupRoute) {
        PanelSetupScreen(onBack = onBack)
    }
}
