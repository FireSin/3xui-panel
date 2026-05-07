package com.firesin.xuipanel.feature.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Gate that wraps the main [content] with a lock screen when app lock is enabled.
 *
 * Rules:
 * - isLockEnabled == false → render [content] directly.
 * - isLockEnabled == true AND not yet authenticated → show [LockScreen].
 * - Authenticated → render [content].
 *
 * Auto-lock on pause:
 * - When both isLockEnabled and isLockOnPauseEnabled are true, ON_STOP resets
 *   authenticated state. On next ON_START the gate sees Idle → shows LockScreen.
 */
@Composable
fun LockGate(
    viewModel: LockViewModel = hiltViewModel(),
    onFinishApp: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isLockEnabled by viewModel.isLockEnabled.collectAsStateWithLifecycle()
    val isLockOnPauseEnabled by viewModel.isLockOnPauseEnabled.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                viewModel.isLockEnabled.value == true &&
                viewModel.isLockOnPauseEnabled.value == true
            ) {
                viewModel.lock()
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    when {
        // Prefs not yet loaded — show nothing (no flash of content before lock can apply).
        isLockEnabled == null -> Unit
        isLockEnabled == false -> content()
        uiState is LockUiState.Authenticated -> content()
        else -> LockScreen(viewModel = viewModel, onFinishApp = onFinishApp)
    }
}
