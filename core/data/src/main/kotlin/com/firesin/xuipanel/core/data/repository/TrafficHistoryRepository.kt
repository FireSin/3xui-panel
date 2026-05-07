package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.xui.dto.InboundDto
import kotlinx.coroutines.flow.Flow

/**
 * Persists traffic samples and exposes daily-aggregated history for charts.
 *
 * [commitSample] is the write path — called by the sampler worker on each tick.
 * [observeInboundDaily] / [observeClientDaily] are the read paths — consumed by the stats UI.
 */
interface TrafficHistoryRepository {

    /**
     * Atomically: upserts traffic_state, increments today's traffic_daily bucket,
     * and purges rows older than 90 days.
     */
    suspend fun commitSample(
        panelId: String,
        inbounds: List<InboundDto>,
        sampledAt: Long,
    ): Result<Unit, DomainError>

    /** Reactive daily points for a given inbound over [fromDay]..[toDay] (UTC midnight epochs). */
    fun observeInboundDaily(
        panelId: String,
        inboundId: Int,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyPoint>>

    /** Reactive daily points for a given client over [fromDay]..[toDay] (UTC midnight epochs). */
    fun observeClientDaily(
        panelId: String,
        inboundId: Int,
        emailKey: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyPoint>>
}

data class DailyPoint(val dayEpoch: Long, val up: Long, val down: Long)
