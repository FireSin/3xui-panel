package com.firesin.xuipanel.feature.settings.xraymetrics.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.xraymetrics.XrayMetricsScreen

const val XrayMetricsRoute = "xray_metrics"

fun NavController.navigateToXrayMetrics() = navigate(XrayMetricsRoute)

fun NavGraphBuilder.xrayMetricsGraph(onBack: () -> Unit) {
    composable(route = XrayMetricsRoute) {
        XrayMetricsScreen(onBack = onBack)
    }
}
