package com.firesin.xuipanel.core.data.repository

import androidx.room.withTransaction
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.db.AppDatabase
import com.firesin.xuipanel.core.data.db.dao.TrafficDailyDao
import com.firesin.xuipanel.core.data.db.dao.TrafficStateDao
import com.firesin.xuipanel.core.data.db.entity.TrafficDailyEntity
import com.firesin.xuipanel.core.data.db.entity.TrafficStateEntity
import com.firesin.xuipanel.core.xui.dto.InboundDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficHistoryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val stateDao: TrafficStateDao,
    private val dailyDao: TrafficDailyDao,
) : TrafficHistoryRepository {

    override suspend fun commitSample(
        panelId: String,
        inbounds: List<InboundDto>,
        sampledAt: Long,
    ): Result<Unit, DomainError> = runCatching {
        val prev = stateDao.getAllForPanel(panelId)
            .associateBy { Pair(it.scopeKind, it.scopeKey) }

        val day = utcMidnight(sampledAt)
        val cutoff = day - RETENTION_MILLIS

        val newStates = mutableListOf<TrafficStateEntity>()
        val dailyDeltas = mutableListOf<TrafficDailyEntity>()

        for (inbound in inbounds) {
            val iKey = inbound.id.toString()
            val pInbound = prev[Pair(SCOPE_INBOUND, iKey)]

            val (dUp, dDown) = computeDelta(
                curUp = inbound.up,
                curDown = inbound.down,
                prevUp = pInbound?.lastUp,
                prevDown = pInbound?.lastDown,
            )

            newStates += TrafficStateEntity(
                panelId = panelId,
                scopeKind = SCOPE_INBOUND,
                scopeKey = iKey,
                inboundId = null,
                lastUp = inbound.up,
                lastDown = inbound.down,
                lastSampledAt = sampledAt,
            )
            dailyDeltas += TrafficDailyEntity(
                panelId = panelId,
                scopeKind = SCOPE_INBOUND,
                scopeKey = iKey,
                inboundId = null,
                dayEpoch = day,
                upDelta = dUp,
                downDelta = dDown,
            )

            for (client in inbound.clientStats.orEmpty()) {
                val cKey = client.email
                val pClient = prev[Pair(SCOPE_CLIENT, cKey)]

                val (cdUp, cdDown) = computeDelta(
                    curUp = client.up,
                    curDown = client.down,
                    prevUp = pClient?.lastUp,
                    prevDown = pClient?.lastDown,
                )

                newStates += TrafficStateEntity(
                    panelId = panelId,
                    scopeKind = SCOPE_CLIENT,
                    scopeKey = cKey,
                    inboundId = client.inboundId,
                    lastUp = client.up,
                    lastDown = client.down,
                    lastSampledAt = sampledAt,
                )
                dailyDeltas += TrafficDailyEntity(
                    panelId = panelId,
                    scopeKind = SCOPE_CLIENT,
                    scopeKey = cKey,
                    inboundId = client.inboundId,
                    dayEpoch = day,
                    upDelta = cdUp,
                    downDelta = cdDown,
                )
            }
        }

        // Atomic transaction: state upsert + daily increment + retention purge
        db.withTransaction {
            stateDao.upsertAll(newStates)
            for (delta in dailyDeltas) {
                dailyDao.insertIgnore(listOf(delta.copy(upDelta = 0L, downDelta = 0L)))
                dailyDao.increment(
                    panelId = delta.panelId,
                    scopeKind = delta.scopeKind,
                    scopeKey = delta.scopeKey,
                    dayEpoch = delta.dayEpoch,
                    upDelta = delta.upDelta,
                    downDelta = delta.downDelta,
                )
            }
            dailyDao.deleteOlderThan(cutoff)
        }
    }.fold(
        onSuccess = { Result.Success(Unit) },
        onFailure = { Result.Failure(DomainError.Unexpected(it)) },
    )

    override fun observeInboundDaily(
        panelId: String,
        inboundId: Int,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyPoint>> =
        dailyDao.observeForInbound(
            panelId = panelId,
            inboundKey = inboundId.toString(),
            fromDay = fromDay,
            toDay = toDay,
        ).map { rows -> rows.map { DailyPoint(it.dayEpoch, it.upDelta, it.downDelta) } }

    override fun observeClientDaily(
        panelId: String,
        inboundId: Int,
        emailKey: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyPoint>> =
        dailyDao.observeForClient(
            panelId = panelId,
            inboundId = inboundId,
            emailKey = emailKey,
            fromDay = fromDay,
            toDay = toDay,
        ).map { rows -> rows.map { DailyPoint(it.dayEpoch, it.upDelta, it.downDelta) } }

    override fun observePanelDaily(
        panelId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DailyPoint>> =
        dailyDao.observeForPanelAggregated(
            panelId = panelId,
            fromDay = fromDay,
            toDay = toDay,
        ).map { rows -> rows.map { DailyPoint(it.dayEpoch, it.upDelta, it.downDelta) } }

    private companion object {
        const val SCOPE_INBOUND = "INBOUND"
        const val SCOPE_CLIENT = "CLIENT"

        /** 90 days in milliseconds */
        const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000

        /** UTC midnight for the given epoch millis */
        fun utcMidnight(epochMillis: Long): Long {
            val msPerDay = 86_400_000L
            return (epochMillis / msPerDay) * msPerDay
        }

        /**
         * Computes delta bytes for a single scope.
         * - prev == null → first sample, delta = 0 (baseline)
         * - cur < prev → counter reset, delta = cur
         * - otherwise → delta = cur - prev
         */
        fun computeDelta(curUp: Long, curDown: Long, prevUp: Long?, prevDown: Long?): Pair<Long, Long> {
            if (prevUp == null || prevDown == null) return 0L to 0L
            val dUp = if (curUp < prevUp) curUp else curUp - prevUp
            val dDown = if (curDown < prevDown) curDown else curDown - prevDown
            return dUp to dDown
        }
    }
}
