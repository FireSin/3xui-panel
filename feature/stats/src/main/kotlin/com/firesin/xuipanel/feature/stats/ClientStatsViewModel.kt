package com.firesin.xuipanel.feature.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class ClientStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: TrafficHistoryRepository,
) : ViewModel() {

    val panelId: String = checkNotNull(savedStateHandle[ARG_PANEL_ID])
    val inboundId: Int = checkNotNull(savedStateHandle[ARG_INBOUND_ID])
    val emailKey: String = checkNotNull(savedStateHandle[ARG_EMAIL_KEY])

    /** Display label for the top app bar (email or remark, URL-decoded by nav-compose). */
    val clientLabel: String = checkNotNull(savedStateHandle[ARG_CLIENT_LABEL])

    private val _range = MutableStateFlow(ChartRange.D7)
    val range: StateFlow<ChartRange> = _range

    fun setRange(range: ChartRange) {
        _range.value = range
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val chartFlow: Flow<List<DailyPoint>> = _range.flatMapLatest { chartRange ->
        val msPerDay = MS_PER_DAY
        val now = System.currentTimeMillis()
        val todayMidnight = (now / msPerDay) * msPerDay
        val fromEpoch = todayMidnight - chartRange.days * msPerDay
        val toEpoch = todayMidnight + msPerDay
        historyRepository.observeClientDaily(panelId, inboundId, emailKey, fromEpoch, toEpoch)
    }

    companion object {
        const val ARG_PANEL_ID = "panelId"
        const val ARG_INBOUND_ID = "inboundId"
        const val ARG_EMAIL_KEY = "emailKey"
        const val ARG_CLIENT_LABEL = "clientLabel"
        private const val MS_PER_DAY = 86_400_000L
    }
}
