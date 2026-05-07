package com.firesin.xuipanel.core.data.repository

import androidx.room.withTransaction
import app.cash.turbine.test
import com.firesin.xuipanel.core.data.db.AppDatabase
import com.firesin.xuipanel.core.data.db.dao.TrafficDailyDao
import com.firesin.xuipanel.core.data.db.dao.TrafficStateDao
import com.firesin.xuipanel.core.data.db.entity.TrafficDailyEntity
import com.firesin.xuipanel.core.xui.dto.InboundDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrafficHistoryRepositoryImplTest {

    private val db: AppDatabase = mockk(relaxed = true)
    private val stateDao: TrafficStateDao = mockk(relaxed = true)
    private val dailyDao: TrafficDailyDao = mockk(relaxed = true)

    private val repo = TrafficHistoryRepositoryImpl(db, stateDao, dailyDao)

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        val txSlot = slot<suspend () -> Unit>()
        coEvery { db.withTransaction(capture(txSlot)) } coAnswers {
            txSlot.captured.invoke()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

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

    // ────────── commitSample retention ──────────

    private fun makeInbound(id: Int = 1) = InboundDto(
        id = id, up = 1000L, down = 2000L, total = 0L,
        remark = "test", enable = true, expiryTime = 0L,
        clientStats = emptyList(), listen = "", port = 0,
        protocol = "vmess", settings = "{}", streamSettings = "{}",
        tag = "tag", sniffing = "{}",
    )

    @Test
    fun `commitSample calls deleteOlderThan with cutoff = utcMidnight minus 90 days`() = runTest {
        val msPerDay = 86_400_000L
        val retentionMillis = 90L * msPerDay
        // Arbitrary timestamp: day 200 at 15:30
        val sampledAt = 200L * msPerDay + 15 * 3600_000L + 30 * 60_000L
        val expectedDay = (sampledAt / msPerDay) * msPerDay          // utcMidnight
        val expectedCutoff = expectedDay - retentionMillis

        coEvery { stateDao.getAllForPanel(any()) } returns emptyList()

        repo.commitSample("panel1", listOf(makeInbound()), sampledAt)

        coVerify(exactly = 1) { dailyDao.deleteOlderThan(expectedCutoff) }
    }

    @Test
    fun `commitSample cutoff is positive when sampledAt is 100 days past epoch`() = runTest {
        val msPerDay = 86_400_000L
        val sampledAt = 100L * msPerDay   // exactly day 100 at midnight

        coEvery { stateDao.getAllForPanel(any()) } returns emptyList()

        repo.commitSample("panel1", listOf(makeInbound()), sampledAt)

        val expectedCutoff = sampledAt - 90L * msPerDay   // = 10 * msPerDay > 0
        coVerify(exactly = 1) { dailyDao.deleteOlderThan(expectedCutoff) }
        assertTrue(expectedCutoff > 0, "cutoff must be positive, was $expectedCutoff")
    }
}
