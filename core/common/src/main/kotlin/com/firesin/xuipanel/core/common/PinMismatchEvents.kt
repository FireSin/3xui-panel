package com.firesin.xuipanel.core.common

import kotlinx.coroutines.flow.SharedFlow

/**
 * Read-only view of the SPKI pin-mismatch event stream.
 *
 * Consumers (e.g. MainActivity) observe [events] to react to mid-session mismatch alerts.
 * The emit capability is restricted to `:core:xui` via [com.firesin.xuipanel.core.xui.PinMismatchEventEmitter].
 */
interface PinMismatchEventBus {
    val events: SharedFlow<PinMismatchEvent>
}

data class PinMismatchEvent(
    val panelId: String,
    val observedSpki: String,
)
