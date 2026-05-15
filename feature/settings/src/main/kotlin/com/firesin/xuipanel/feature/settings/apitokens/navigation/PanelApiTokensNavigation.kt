package com.firesin.xuipanel.feature.settings.apitokens.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.apitokens.PanelApiTokensScreen

const val PanelApiTokensRoute = "api_tokens"

fun NavController.navigateToPanelApiTokens() = navigate(PanelApiTokensRoute)

fun NavGraphBuilder.panelApiTokensGraph(
    onBack: () -> Unit,
) {
    composable(route = PanelApiTokensRoute) {
        PanelApiTokensScreen(onBack = onBack)
    }
}
