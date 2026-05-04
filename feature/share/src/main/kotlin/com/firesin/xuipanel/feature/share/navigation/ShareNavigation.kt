package com.firesin.xuipanel.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firesin.xuipanel.feature.share.ClientShareScreen
import java.net.URLEncoder

// Route constants

const val ShareRoute = "share/{inboundId}/{clientKey}"

private const val ARG_INBOUND_ID = "inboundId"
private const val ARG_CLIENT_KEY = "clientKey"

// NavController extensions

fun NavController.navigateToShare(inboundId: Int, clientKey: String) {
    val encoded = URLEncoder.encode(clientKey, "UTF-8")
    navigate("share/$inboundId/$encoded")
}

// Graph registration

fun NavGraphBuilder.shareGraph(navController: NavController) {
    composable(
        route = ShareRoute,
        arguments = listOf(
            navArgument(ARG_INBOUND_ID) { type = NavType.IntType },
            navArgument(ARG_CLIENT_KEY) { type = NavType.StringType },
        ),
    ) {
        // navigateToShare percent-encoded clientKey; nav-compose decodes path args
        // before populating SavedStateHandle, so the ViewModel sees the raw urlKey.
        ClientShareScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
