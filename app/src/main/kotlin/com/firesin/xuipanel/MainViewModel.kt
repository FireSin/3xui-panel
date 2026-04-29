package com.firesin.xuipanel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firesin.xuipanel.core.common.PinMismatchEvent
import com.firesin.xuipanel.core.common.PinMismatchEventBus
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.xui.XuiSessionCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import java.util.UUID
import javax.inject.Inject

data class PinMismatchDialogInfo(
    val event: PinMismatchEvent,
    val panelName: String,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val pinMismatchEvents: PinMismatchEventBus,
    private val panelRepository: PanelRepository,
    private val clientFactory: OkHttpClientFactory,
    private val sessionCache: XuiSessionCache,
) : ViewModel() {

    private val dialogQueue = ArrayDeque<PinMismatchDialogInfo>()
    private val queueMutex = Mutex()

    private val _pinMismatchDialog = MutableStateFlow<PinMismatchDialogInfo?>(null)
    val pinMismatchDialog: StateFlow<PinMismatchDialogInfo?> = _pinMismatchDialog

    init {
        viewModelScope.launch {
            pinMismatchEvents.events.collect { event ->
                if (!isValidUuid(event.panelId)) {
                    Log.w("MainViewModel", "PinMismatchEvent dropped: panelId is not a valid UUID")
                    return@collect
                }
                clientFactory.invalidate(event.panelId)
                sessionCache.invalidate(event.panelId)
                val name = panelRepository.get(event.panelId)?.name ?: event.panelId
                val info = PinMismatchDialogInfo(event = event, panelName = name)
                queueMutex.withLock {
                    dialogQueue.addLast(info)
                    if (_pinMismatchDialog.value == null) {
                        _pinMismatchDialog.value = dialogQueue.firstOrNull()
                    }
                }
            }
        }
    }

    private fun isValidUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess

    fun dismissPinMismatchDialog() {
        viewModelScope.launch {
            queueMutex.withLock {
                dialogQueue.removeFirstOrNull()
                _pinMismatchDialog.value = dialogQueue.firstOrNull()
            }
        }
    }
}
