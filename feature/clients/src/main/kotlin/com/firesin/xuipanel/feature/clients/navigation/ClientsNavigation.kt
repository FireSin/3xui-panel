package com.firesin.xuipanel.feature.clients.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.clients.ClientsScreen

fun NavGraphBuilder.clientsGraph(navController: NavController) {
    composable(route = "clients") {
        ClientsScreen()
    }
}
