package com.firesin.xuipanel.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.SettingsScreen
import com.firesin.xuipanel.feature.settings.apitokens.navigation.PanelApiTokensRoute
import com.firesin.xuipanel.feature.settings.apitokens.navigation.panelApiTokensGraph
import com.firesin.xuipanel.feature.settings.cryptogen.navigation.CryptoGenRoute
import com.firesin.xuipanel.feature.settings.cryptogen.navigation.cryptoGenGraph
import com.firesin.xuipanel.feature.settings.outbounds.navigation.OutboundsRoute
import com.firesin.xuipanel.feature.settings.outbounds.navigation.outboundsGraph
import com.firesin.xuipanel.feature.settings.warpnord.navigation.WarpNordRoute
import com.firesin.xuipanel.feature.settings.warpnord.navigation.warpNordGraph
import com.firesin.xuipanel.feature.settings.xraytemplate.navigation.XrayTemplateRoute
import com.firesin.xuipanel.feature.settings.xraytemplate.navigation.xrayTemplateGraph
import com.firesin.xuipanel.feature.settings.xraymetrics.navigation.XrayMetricsRoute
import com.firesin.xuipanel.feature.settings.xraymetrics.navigation.xrayMetricsGraph
import com.firesin.xuipanel.feature.settings.geo.navigation.GeoSourcesRoute
import com.firesin.xuipanel.feature.settings.geo.navigation.geoSourcesGraph
import com.firesin.xuipanel.feature.settings.panelsetup.navigation.PanelSetupRoute
import com.firesin.xuipanel.feature.settings.panelsetup.navigation.panelSetupGraph

const val SettingsRoute = "settings"

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onMenuClick: () -> Unit = {},
) {
    composable(route = SettingsRoute) {
        SettingsScreen(
            onMenuClick = onMenuClick,
            onNavigateToGeoSources = { navController.navigate(GeoSourcesRoute) },
            onNavigateToApiTokens = { navController.navigate(PanelApiTokensRoute) },
            onNavigateToPanelSetup = { navController.navigate(PanelSetupRoute) },
            onNavigateToCryptoGen = { navController.navigate(CryptoGenRoute) },
            onNavigateToOutbounds = { navController.navigate(OutboundsRoute) },
            onNavigateToXrayMetrics = { navController.navigate(XrayMetricsRoute) },
            onNavigateToWarpNord = { navController.navigate(WarpNordRoute) },
            onNavigateToXrayTemplate = { navController.navigate(XrayTemplateRoute) },
        )
    }
    geoSourcesGraph(onBack = { navController.popBackStack() })
    panelApiTokensGraph(onBack = { navController.popBackStack() })
    panelSetupGraph(onBack = { navController.popBackStack() })
    cryptoGenGraph(onBack = { navController.popBackStack() })
    outboundsGraph(onBack = { navController.popBackStack() })
    xrayMetricsGraph(onBack = { navController.popBackStack() })
    warpNordGraph(onBack = { navController.popBackStack() })
    xrayTemplateGraph(onBack = { navController.popBackStack() })
}
