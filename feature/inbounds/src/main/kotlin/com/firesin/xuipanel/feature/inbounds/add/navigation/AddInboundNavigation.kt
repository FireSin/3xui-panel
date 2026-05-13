package com.firesin.xuipanel.feature.inbounds.add.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.inbounds.add.AddInboundScreen

const val AddInboundRoute = "inbounds_add"

fun NavController.navigateToAddInbound() {
    navigate(AddInboundRoute)
}

fun NavGraphBuilder.addInboundGraph(
    navController: NavController,
    onSaved: () -> Unit = {},
) {
    composable(route = AddInboundRoute) {
        AddInboundScreen(
            onClose = { navController.popBackStack() },
            onSaved = {
                navController.popBackStack()
                onSaved()
            },
        )
    }
}
