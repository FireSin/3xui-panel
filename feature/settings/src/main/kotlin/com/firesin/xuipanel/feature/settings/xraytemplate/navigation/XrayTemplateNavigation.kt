package com.firesin.xuipanel.feature.settings.xraytemplate.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.xraytemplate.XrayTemplateScreen

const val XrayTemplateRoute = "xray_template"

fun NavController.navigateToXrayTemplate() = navigate(XrayTemplateRoute)

fun NavGraphBuilder.xrayTemplateGraph(onBack: () -> Unit) {
    composable(route = XrayTemplateRoute) {
        XrayTemplateScreen(onBack = onBack)
    }
}
