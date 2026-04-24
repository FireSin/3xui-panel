package com.firesin.xuipanel.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.share.ShareScreen

fun NavGraphBuilder.shareGraph(navController: NavController) {
    composable(route = "share") {
        ShareScreen()
    }
}
