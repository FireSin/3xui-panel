package com.firesin.xuipanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.lock.LockGate
import com.firesin.xuipanel.feature.panels.navigation.panelEditRoute
import com.firesin.xuipanel.navigation.XuiNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            XuiPanelTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val pinMismatchDialog by viewModel.pinMismatchDialog.collectAsStateWithLifecycle()
                pinMismatchDialog?.let { info ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissPinMismatchDialog,
                        title = { Text(stringResource(R.string.global_pin_mismatch_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.global_pin_mismatch_message,
                                    info.panelName,
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.dismissPinMismatchDialog()
                                    navController.navigate(panelEditRoute(info.event.panelId))
                                },
                            ) {
                                Text(stringResource(R.string.global_pin_mismatch_go_to_settings))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissPinMismatchDialog) {
                                Text(stringResource(R.string.global_pin_mismatch_cancel))
                            }
                        },
                    )
                }
                LockGate(onFinishApp = ::finishAffinity) {
                    XuiNavHost(navController = navController)
                }
            }
        }
    }
}
