package com.firesin.xuipanel.core.data.repository

import app.cash.turbine.test
import com.firesin.xuipanel.core.data.db.AppDatabase
import com.firesin.xuipanel.core.data.db.dao.TrafficDailyDao
import com.firesin.xuipanel.core.data.db.dao.TrafficStateDao
import com.firesin.xuipanel.core.data.db.entity.TrafficDailyEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficHistoryRepositoryImplTest {

    private val db: AppDatabase = mockk(relaxed = true)
    private val stateDao: TrafficStateDao = mockk(relaxed = true)
    private val dailyDao: TrafficDailyDao = mockk(relaxed = true)

    private val repo = TrafficHistoryRepositoryImpl(db, stateDao, dailyDao)

    // ────────── observeInboundDaily ──────────

    @Test
    fun `observeInboundDaily maps entities to DailyPoints correctly`() = runTest {
        val panelId = "p1"
        val inboundId = 42
        val fromDay = 1_000_000L
        val toDay = 2_000_000L

        val entity = TrafficDailyEntity(
            panelId = panelId,
            scopeKind = "INBOUND",
            scopeKey = inboundId.toString(),
            inboundId = null,
            dayEpoch = 1_500_000L,
            upDelta = 1_024L,
            downDelta = 4_096L,
        )
        every {
            dailyDao.observeForInbound(panelId, inboundId.toString(), fromDay, toDay)
        } returns flowOf(listOf(entity))

        repo.observeInboundDaily(panelId, inboundId, fromDay, toDay).test {
            val points = awaitItem()
            assertEquals(1, points.size)
            assertEquals(DailyPoint(dayEpoch = 1_500_000L, up = 1_024L, down = 4_096L), points[0])
            awaitComplete()
        }
    }

    @Test
    fun `observeInboundDaily emits empty list when dao returns empty`() = runTest {
        val panelId = "p1"
        every {
            dailyDao.observeForInbound(panelId, "7", 0L, 100L)
        } returns flowOf(emptyList())

        repo.observeInboundDaily(panelId, 7, 0L, 100L).test {
            assertEquals(emptyList<DailyPoint>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `observeInboundDaily maps multiple entities preserving order`() = runTest {
        val panelId = "p2"
        val inboundId = 3
        val msPerDay = 86_400_000L
        val day1 = msPerDay
        val day2 = msPerDay * 2

        val entities = listOf(
            TrafficDailyEntity(panelId, "INBOUND", "3", null, day1, 100L, 200L),
            TrafficDailyEntity(panelId, "INBOUND", "3", null, day2, 300L, 400L),
        )
        every {
            dailyDao.observeForInbound(panelId, "3", 0L, msPerDay * 3)
        } returns flowOf(entities)

        repo.observeInboundDaily(panelId, inboundId, 0L, msPerDay * 3).test {
            val points = awaitItem()
            assertEquals(2, points.size)
            assertEquals(DailyPoint(day1, 100L, 200L), points[0])
            assertEquals(DailyPoint(day2, 300L, 400L), points[1])
            awaitComplete()
        }
    }
}
