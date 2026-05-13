package com.firesin.xuipanel.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
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
    installId: String = "",
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val onMenuClick: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = Color(0xFF3FD89B),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "3xui Panel",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "v0.4.x · MVP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
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

                Spacer(Modifier.weight(1f))

                if (installId.isNotEmpty()) {
                    DrawerInstallIdFooter(installId = installId)
                }
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
                onMenuClick = onMenuClick,
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

@Composable
private fun DrawerInstallIdFooter(installId: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val short = installId.takeLast(8)
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(installId))
                Toast.makeText(context, "ID скопирован", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ID для саппорта",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "…$short",
            fontSize = 12.sp,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
