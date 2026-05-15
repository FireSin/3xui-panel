package com.firesin.xuipanel.core.common

import kotlinx.coroutines.flow.SharedFlow

/**
 * Read-only view of WebSocket-driven UI signals from the active panel.
 *
 * - [notifications]: panel-pushed `notification` events that should surface as a snackbar/toast.
 * - [invalidations]: panel-pushed `invalidate` events whose payload is a resource name
 *   (e.g. `"inbounds"`, `"clients"`, `"settings"`). ViewModels matching that resource
 *   should refresh their data.
 *
 * The emit capability is restricted to `:core:xui` via `WsUiEventDispatcher`.
 */
interface WsUiEventBus {
    val notifications: SharedFlow<WsNotification>
    val invalidations: SharedFlow<String>
}

data class WsNotification(
    val title: String,
    val body: String,
    val severity: String,
)
