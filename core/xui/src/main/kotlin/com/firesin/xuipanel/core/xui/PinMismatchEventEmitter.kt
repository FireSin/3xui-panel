package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.PinMismatchEvent
import com.firesin.xuipanel.core.common.PinMismatchEventBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that satisfies the public [PinMismatchEventBus] interface (read-only, app-wide)
 * and exposes an internal [emit] for `:core:xui` callers only.
 *
 * [PinMismatchEventBus] is bound via Hilt (see [XuiModule]). The dispatcher class itself is
 * `internal`, so no code outside `:core:xui` can inject it directly or call [emit].
 */
@Singleton
class PinMismatchEventDispatcher @Inject constructor() : PinMismatchEventBus {
    private val _events = MutableSharedFlow<PinMismatchEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PinMismatchEvent> = _events.asSharedFlow()
    suspend fun emit(event: PinMismatchEvent) { _events.emit(event) }
}
