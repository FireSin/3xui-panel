package com.firesin.xuipanel.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.WsNotification
import com.firesin.xuipanel.core.common.WsUiEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bridge ViewModel that re-exposes the panel-pushed [WsUiEventBus.notifications] stream
 * scoped to the activity, so a single global snackbar host can react to it.
 */
@HiltViewModel
class WsToastViewModel @Inject constructor(
    bus: WsUiEventBus,
) : ViewModel() {

    private val _events = MutableSharedFlow<WsNotification>(extraBufferCapacity = 8)
    val events: SharedFlow<WsNotification> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            bus.notifications.collect { _events.emit(it) }
        }
    }
}

/**
 * Renders a Material3 [SnackbarHost] that pops a toast for every panel-pushed
 * `notification` WS event. Designed to live in the outer app Scaffold's `snackbarHost` slot.
 */
@Composable
fun WsToastHost(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: WsToastViewModel = hiltViewModel(),
) {
    LaunchedEffect(snackbarHostState) {
        viewModel.events.collect { n ->
            val label = listOf(n.title, n.body).filter { it.isNotBlank() }.joinToString(" — ")
            if (label.isNotBlank()) {
                snackbarHostState.showSnackbar(
                    message = label,
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
    SnackbarHost(hostState = snackbarHostState)
}
