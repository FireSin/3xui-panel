package com.firesin.xuipanel.feature.nodes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firesin.xuipanel.feature.nodes.NodeDetailScreen
import com.firesin.xuipanel.feature.nodes.NodeFormScreen
import com.firesin.xuipanel.feature.nodes.NodesListScreen

const val NodesListRoute = "nodes_list"
const val NodesFormAddRoute = "nodes_form"
const val NodesFormEditRoute = "nodes_form/{nodeId}"
const val NodesDetailRoute = "nodes_detail/{nodeId}"

fun NavController.navigateToAddNode() {
    navigate(NodesFormAddRoute)
}

fun NavController.navigateToEditNode(nodeId: Int) {
    navigate("nodes_form/$nodeId")
}

fun NavController.navigateToNodeDetail(nodeId: Int) {
    navigate("nodes_detail/$nodeId")
}

fun NavGraphBuilder.nodesGraph(
    navController: NavController,
    onMenuClick: () -> Unit = {},
) {
    composable(route = NodesListRoute) {
        NodesListScreen(
            onMenuClick = onMenuClick,
            onAddNode = { navController.navigateToAddNode() },
            onEditNode = { nodeId -> navController.navigateToEditNode(nodeId) },
            onViewDetail = { nodeId -> navController.navigateToNodeDetail(nodeId) },
        )
    }

    composable(route = NodesFormAddRoute) {
        NodeFormScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(
        route = NodesFormEditRoute,
        arguments = listOf(navArgument("nodeId") { type = NavType.IntType }),
    ) {
        NodeFormScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(
        route = NodesDetailRoute,
        arguments = listOf(navArgument("nodeId") { type = NavType.IntType }),
    ) { backStackEntry ->
        val nodeId = backStackEntry.arguments?.getInt("nodeId") ?: return@composable
        NodeDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditNode = { navController.navigateToEditNode(nodeId) },
        )
    }
}
