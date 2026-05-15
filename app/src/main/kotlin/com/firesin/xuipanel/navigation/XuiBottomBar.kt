package com.firesin.xuipanel.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.firesin.xuipanel.feature.dashboard.navigation.DashboardRoute
import com.firesin.xuipanel.feature.inbounds.navigation.InboundsRoute
import com.firesin.xuipanel.feature.nodes.navigation.NodesListRoute
import com.firesin.xuipanel.feature.panels.navigation.PanelsListRoute
import com.firesin.xuipanel.feature.settings.navigation.SettingsRoute

internal data class XuiTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

internal val XuiBottomTabs: List<XuiTab> = listOf(
    XuiTab(DashboardRoute, "Главная", Icons.Default.Home),
    XuiTab(InboundsRoute, "Подключения", Icons.AutoMirrored.Filled.List),
    XuiTab(NodesListRoute, "Ноды", Icons.Default.Cloud),
    XuiTab(PanelsListRoute, "Панели", Icons.Default.Dns),
    XuiTab(SettingsRoute, "Настройки", Icons.Default.Settings),
)

internal val XuiTopLevelRoutes: Set<String> = XuiBottomTabs.map { it.route }.toSet()

@Composable
internal fun XuiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        XuiBottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    // «Подключения» (11 chars) is wider than the slot at default
                    // labelSmall size and was wrapping to two lines. Drop the
                    // font a hair and force single-line ellipsis.
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    )
                },
                colors = NavigationBarItemDefaults.colors(),
            )
        }
    }
}
