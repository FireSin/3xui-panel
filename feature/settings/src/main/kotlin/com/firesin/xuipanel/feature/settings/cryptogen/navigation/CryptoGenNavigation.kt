package com.firesin.xuipanel.feature.settings.cryptogen.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.firesin.xuipanel.feature.settings.cryptogen.CryptoGenScreen

const val CryptoGenRoute = "crypto_gen"

fun NavController.navigateToCryptoGen() = navigate(CryptoGenRoute)

fun NavGraphBuilder.cryptoGenGraph(onBack: () -> Unit) {
    composable(route = CryptoGenRoute) {
        CryptoGenScreen(onBack = onBack)
    }
}
