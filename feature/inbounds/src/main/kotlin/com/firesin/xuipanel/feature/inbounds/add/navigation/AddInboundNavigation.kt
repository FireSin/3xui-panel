package com.firesin.xuipanel.feature.inbounds.add.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firesin.xuipanel.feature.inbounds.add.AddInboundScreen

const val AddInboundRoute = "inbounds_add"
const val EditInboundRoute = "inbound_edit/{inboundId}"

fun NavController.navigateToAddInbound() {
    navigate(AddInboundRoute)
}

fun NavController.navigateToEditInbound(inboundId: Int) {
    navigate("inbound_edit/$inboundId")
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

    composable(
        route = EditInboundRoute,
        arguments = listOf(navArgument("inboundId") { type = NavType.IntType }),
    ) {
        AddInboundScreen(
            onClose = { navController.popBackStack() },
            onSaved = {
                navController.popBackStack()
                onSaved()
            },
        )
    }
}
