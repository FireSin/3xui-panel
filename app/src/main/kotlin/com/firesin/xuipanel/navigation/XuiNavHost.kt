package com.firesin.xuipanel.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.firesin.xuipanel.feature.clients.navigation.clientsGraph
import com.firesin.xuipanel.feature.dashboard.navigation.DashboardRoute
import com.firesin.xuipanel.feature.dashboard.navigation.dashboardGraph
import com.firesin.xuipanel.feature.inbounds.navigation.InboundsRoute
import com.firesin.xuipanel.feature.inbounds.navigation.inboundsGraph
import com.firesin.xuipanel.feature.inbounds.navigation.navigateToManageClients
import com.firesin.xuipanel.feature.panels.navigation.PanelAddRoute
import com.firesin.xuipanel.feature.panels.navigation.PanelsListRoute
import com.firesin.xuipanel.feature.panels.navigation.PanelsRoute
import com.firesin.xuipanel.feature.panels.navigation.panelsGraph
import com.firesin.xuipanel.feature.settings.navigation.settingsGraph
import com.firesin.xuipanel.feature.share.navigation.navigateToShare
import com.firesin.xuipanel.feature.share.navigation.shareGraph
import com.firesin.xuipanel.feature.stats.navigation.clientStatsGraph
import com.firesin.xuipanel.feature.stats.navigation.navigateToClientStats
import com.firesin.xuipanel.feature.stats.navigation.statsGraph
import kotlinx.coroutines.launch

@Composable
fun XuiNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val onMenuClick: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Главная") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    selected = currentRoute == DashboardRoute,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(DashboardRoute) {
                            launchSingleTop = true
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Подключения") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    selected = currentRoute == InboundsRoute,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(InboundsRoute) {
                            launchSingleTop = true
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Панели") },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    selected = currentRoute == PanelsListRoute,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(PanelsListRoute) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = PanelsRoute,
        ) {
            panelsGraph(
                navController = navController,
                onNavigateToSettings = { navController.navigate("settings") },
            )
            dashboardGraph(
                navController = navController,
                panelsAddRoute = PanelAddRoute,
                onNavigateToStats = { navController.navigate("stats") },
                onNavigateToInbounds = { navController.navigate(InboundsRoute) },
                onMenuClick = onMenuClick,
            )
            inboundsGraph(
                navController = navController,
                onManageClients = { inboundId -> navController.navigateToManageClients(inboundId) },
                onMenuClick = onMenuClick,
            )
            clientsGraph(
                navController = navController,
                panelsAddRoute = PanelAddRoute,
                onNavigateShare = { inboundId, clientKey ->
                    navController.navigateToShare(inboundId, clientKey)
                },
            )
            shareGraph(navController)
            statsGraph(navController)
            clientStatsGraph(navController)
            settingsGraph(onMenuClick = onMenuClick)
        }
    }
}
