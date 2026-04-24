package com.firesin.xuipanel.feature.inbounds.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.inbounds.InboundsScreen

fun NavGraphBuilder.inboundsGraph(navController: NavController) {
    composable(route = "inbounds") {
        InboundsScreen()
    }
}
