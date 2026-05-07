package com.firesin.xuipanel.feature.stats.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.firesin.xuipanel.feature.stats.ClientStatsScreen
import com.firesin.xuipanel.feature.stats.ClientStatsViewModel
import com.firesin.xuipanel.feature.stats.StatsScreen
import java.net.URLEncoder

// ── Main stats screen ─────────────────────────────────────────────────────────

fun NavGraphBuilder.statsGraph(navController: NavController) {
    composable(route = "stats") {
        StatsScreen(
            onNavigateToClientStats = { panelId, inboundId, emailKey, clientLabel ->
                navController.navigateToClientStats(panelId, inboundId, emailKey, clientLabel)
            },
        )
    }
}

// ── Client stats drill-down ───────────────────────────────────────────────────

private const val CLIENT_STATS_ROUTE =
    "stats/client/{${ClientStatsViewModel.ARG_PANEL_ID}}" +
    "/{${ClientStatsViewModel.ARG_INBOUND_ID}}" +
    "/{${ClientStatsViewModel.ARG_EMAIL_KEY}}" +
    "/{${ClientStatsViewModel.ARG_CLIENT_LABEL}}"

fun NavController.navigateToClientStats(
    panelId: String,
    inboundId: Int,
    emailKey: String,
    clientLabel: String,
) {
    val encodedEmail = URLEncoder.encode(emailKey, "UTF-8")
    val encodedLabel = URLEncoder.encode(clientLabel, "UTF-8")
    navigate("stats/client/$panelId/$inboundId/$encodedEmail/$encodedLabel")
}

fun NavGraphBuilder.clientStatsGraph(navController: NavController) {
    composable(
        route = CLIENT_STATS_ROUTE,
        arguments = listOf(
            navArgument(ClientStatsViewModel.ARG_PANEL_ID) { type = NavType.StringType },
            navArgument(ClientStatsViewModel.ARG_INBOUND_ID) { type = NavType.IntType },
            navArgument(ClientStatsViewModel.ARG_EMAIL_KEY) { type = NavType.StringType },
            navArgument(ClientStatsViewModel.ARG_CLIENT_LABEL) { type = NavType.StringType },
        ),
    ) {
        ClientStatsScreen(onBack = { navController.popBackStack() })
    }
}
