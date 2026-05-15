package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.WsNotification
import com.firesin.xuipanel.core.common.WsUiEventBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton hub for WS-pushed UI signals. The dispatcher is `:core:xui`-private; the
 * read-only [WsUiEventBus] is bound via Hilt for app-wide consumers.
 *
 * Emission happens from the same place that owns the WS subscription (currently the
 * dashboard VM — only one active panel at a time).
 */
@Singleton
class WsUiEventDispatcher @Inject constructor() : WsUiEventBus {
    private val _notifications = MutableSharedFlow<WsNotification>(extraBufferCapacity = 16)
    override val notifications: SharedFlow<WsNotification> = _notifications.asSharedFlow()

    private val _invalidations = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val invalidations: SharedFlow<String> = _invalidations.asSharedFlow()

    suspend fun emitNotification(event: WsNotification) { _notifications.emit(event) }
    suspend fun emitInvalidation(resource: String) { _invalidations.emit(resource) }
}
