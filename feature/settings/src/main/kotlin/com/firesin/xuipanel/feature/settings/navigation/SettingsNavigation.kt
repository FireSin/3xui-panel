package com.firesin.xuipanel.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.SettingsScreen

const val SettingsRoute = "settings"

fun NavGraphBuilder.settingsGraph(
    onMenuClick: () -> Unit,
) {
    composable(route = SettingsRoute) {
        SettingsScreen(onMenuClick = onMenuClick)
    }
}
