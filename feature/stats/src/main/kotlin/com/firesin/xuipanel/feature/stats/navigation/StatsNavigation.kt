package com.firesin.xuipanel.feature.stats.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.stats.StatsScreen

fun NavGraphBuilder.statsGraph(navController: NavController) {
    composable(route = "stats") {
        StatsScreen()
    }
}
