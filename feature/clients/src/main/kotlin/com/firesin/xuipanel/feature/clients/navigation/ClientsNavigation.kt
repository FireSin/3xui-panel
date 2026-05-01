package com.firesin.xuipanel.feature.clients.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.firesin.xuipanel.core.xui.dto.ClientConfig
import com.firesin.xuipanel.core.xui.dto.urlKey
import com.firesin.xuipanel.feature.clients.ClientFormScreen
import com.firesin.xuipanel.feature.clients.ClientsScreen
import com.firesin.xuipanel.feature.clients.ui.ClientsUiState
import com.firesin.xuipanel.feature.clients.ui.ClientsViewModel
import java.net.URLDecoder
import java.net.URLEncoder

// Route constants

const val ClientsListRoute = "clients/{inboundId}"
private const val ARG_INBOUND_ID = "inboundId"
private const val ARG_CLIENT_KEY = "clientKey"

private const val ClientsAddRoute = "clients/{inboundId}/add"
private const val ClientsEditRoute = "clients/{inboundId}/edit/{clientKey}"

// NavController extensions

fun NavController.navigateToClients(inboundId: Int = -1) {
    navigate("clients/$inboundId")
}

fun NavController.navigateToClientsRoot() {
    navigate("clients/-1")
}

fun NavController.navigateToAddClient(inboundId: Int) {
    navigate("clients/$inboundId/add")
}

fun NavController.navigateToEditClient(inboundId: Int, clientKey: String) {
    val encoded = URLEncoder.encode(clientKey, "UTF-8")
    navigate("clients/$inboundId/edit/$encoded")
}

// Graph registration

fun NavGraphBuilder.clientsGraph(
    navController: NavController,
    panelsAddRoute: String = "panels_add",
) {
    // List screen — inboundId == -1 means no pre-selection
    composable(
        route = ClientsListRoute,
        arguments = listOf(
            navArgument(ARG_INBOUND_ID) {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ) { backStackEntry ->
        val rawId = backStackEntry.arguments?.getInt(ARG_INBOUND_ID) ?: -1
        val initialId = if (rawId == -1) null else rawId
        ClientsScreen(
            initialInboundId = initialId,
            onAddPanel = { navController.navigate(panelsAddRoute) },
            onNavigateAdd = { inboundId -> navController.navigateToAddClient(inboundId) },
            onNavigateEdit = { inboundId, key -> navController.navigateToEditClient(inboundId, key) },
        )
    }

    // Add client form
    composable(
        route = ClientsAddRoute,
        arguments = listOf(
            navArgument(ARG_INBOUND_ID) { type = NavType.IntType },
        ),
    ) { backStackEntry ->
        val inboundId = backStackEntry.arguments?.getInt(ARG_INBOUND_ID) ?: return@composable

        // Shared ViewModel scoped to the parent list destination so add refreshes the list
        val listEntry = navController.getBackStackEntry(ClientsListRoute)
        val viewModel = hiltViewModel<ClientsViewModel>(listEntry)
        val uiState = viewModel.uiState.value
        val content = uiState as? ClientsUiState.Content
        val inbound = content?.inbounds?.firstOrNull { it.id == inboundId }

        if (inbound != null) {
            ClientFormScreen(
                protocol = inbound.protocol,
                existingClient = null,
                onSubmit = { config ->
                    viewModel.addClient(inboundId, config)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }

    // Edit client form
    composable(
        route = ClientsEditRoute,
        arguments = listOf(
            navArgument(ARG_INBOUND_ID) { type = NavType.IntType },
            navArgument(ARG_CLIENT_KEY) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val inboundId = backStackEntry.arguments?.getInt(ARG_INBOUND_ID) ?: return@composable
        val encodedKey = backStackEntry.arguments?.getString(ARG_CLIENT_KEY) ?: return@composable
        val clientKey = URLDecoder.decode(encodedKey, "UTF-8")

        val listEntry = navController.getBackStackEntry(ClientsListRoute)
        val viewModel = hiltViewModel<ClientsViewModel>(listEntry)
        val uiState = viewModel.uiState.value
        val content = uiState as? ClientsUiState.Content
        val inbound = content?.inbounds?.firstOrNull { it.id == inboundId }
        val existing = content?.clients?.firstOrNull { it.emailOrId() == clientKey }

        if (inbound != null && existing != null) {
            ClientFormScreen(
                protocol = inbound.protocol,
                existingClient = existing,
                onSubmit = { config ->
                    viewModel.updateClient(inboundId, clientKey, config)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}

/** Returns the urlKey-equivalent for matching in edit: UUID for vmess/vless, email for SS. */
private fun ClientConfig.emailOrId(): String = urlKey
