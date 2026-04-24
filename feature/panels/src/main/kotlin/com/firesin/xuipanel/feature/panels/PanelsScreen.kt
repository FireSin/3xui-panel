package com.firesin.xuipanel.feature.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.panels.navigation.DashboardRoute
import com.firesin.xuipanel.feature.panels.navigation.InboundsRoute
import com.firesin.xuipanel.feature.panels.navigation.ClientsRoute
import com.firesin.xuipanel.feature.panels.navigation.ShareRoute
import com.firesin.xuipanel.feature.panels.navigation.StatsRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "3xui Panel") })
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "MVP in development",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.navigate(DashboardRoute) }) {
                Text("Dashboard")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { navController.navigate(InboundsRoute) }) {
                Text("Inbounds")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { navController.navigate(ClientsRoute) }) {
                Text("Clients")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { navController.navigate(ShareRoute) }) {
                Text("Share")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { navController.navigate(StatsRoute) }) {
                Text("Stats")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PanelsScreenPreview() {
    XuiPanelTheme {
        // NavController cannot be used in preview; use a stub.
        PanelsScreenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelsScreenContent() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "3xui Panel") })
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "MVP in development",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
