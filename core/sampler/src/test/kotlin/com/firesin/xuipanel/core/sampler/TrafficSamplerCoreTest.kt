package com.firesin.xuipanel.core.sampler

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.InboundDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class TrafficSamplerCoreTest {

    private val panelRepository = mockk<PanelRepository>()
    private val xuiClient = mockk<XuiClient>()
    private val historyRepository = mockk<TrafficHistoryRepository>()
    private val clock = FixedClock(nowMs = 1_700_000_000_000L)

    private val core = TrafficSamplerCore(
        panelRepository = panelRepository,
        xuiClient = xuiClient,
        historyRepository = historyRepository,
        clock = clock,
    )

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private fun panel(id: String = "panel-1") = Panel(
        id = id,
        name = "Test Panel $id",
        baseUrl = "https://panel.example.com/",
        login = "admin",
        password = "secret",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = true,
        createdAt = Instant.EPOCH,
        lastLoginAt = null,
    )

    private fun inbound(id: Int = 1) = InboundDto(
        id = id, up = 100L, down = 200L, total = 0L,
        remark = "test", enable = true, expiryTime = 0L,
        clientStats = emptyList(), listen = "", port = 443,
        protocol = "vless", settings = "{}", streamSettings = "{}",
        tag = "tag-$id", sniffing = "{}",
    )

    @BeforeEach
    fun setUp() {
        coEvery {
            historyRepository.commitSample(any(), any(), any())
        } returns Result.Success(Unit)
    }

    // ── No panels ─────────────────────────────────────────────────────────────

    @Test
    fun `returns NoPanels when panel list is empty`() = runTest {
        coEvery { panelRepository.observeAll() } returns flowOf(emptyList())

        val outcome = core.runOnce()

        assertEquals(TrafficSamplerCore.Outcome.NoPanels, outcome)
        coVerify(exactly = 0) { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) }
    }

    // ── All panels succeed ────────────────────────────────────────────────────

    @Test
    fun `returns PartialOrFullSuccess when all panels succeed`() = runTest {
        val panels = listOf(panel("p1"), panel("p2"))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)

        coEvery {
            xuiClient.fetchInbounds(any(), any(), any(), any(), any())
        } returns Result.Success(listOf(inbound()))

        val outcome = core.runOnce()

        assertEquals(TrafficSamplerCore.Outcome.PartialOrFullSuccess, outcome)
        coVerify(exactly = 2) { xuiClient.fetchInbounds(any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { historyRepository.commitSample(any(), any(), any()) }
    }

    // ── Partial success ───────────────────────────────────────────────────────

    @Test
    fun `returns PartialOrFullSuccess when only one of two panels succeeds`() = runTest {
        val panels = listOf(panel("p1"), panel("p2"))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)

        coEvery {
            xuiClient.fetchInbounds(eq("p1"), any(), any(), any(), any())
        } returns Result.Failure(DomainError.InvalidCredentials)

        coEvery {
            xuiClient.fetchInbounds(eq("p2"), any(), any(), any(), any())
        } returns Result.Success(listOf(inbound()))

        val outcome = core.runOnce()

        assertEquals(TrafficSamplerCore.Outcome.PartialOrFullSuccess, outcome)
        // p2 must still be attempted even after p1 failure
        coVerify(exactly = 1) { xuiClient.fetchInbounds(eq("p2"), any(), any(), any(), any()) }
        coVerify(exactly = 1) { historyRepository.commitSample(eq("p2"), any(), any()) }
        coVerify(exactly = 0) { historyRepository.commitSample(eq("p1"), any(), any()) }
    }

    // ── All panels fail ───────────────────────────────────────────────────────

    @Test
    fun `returns AllFailed when all panels return network error`() = runTest {
        val panels = listOf(panel("p1"), panel("p2"))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)

        coEvery {
            xuiClient.fetchInbounds(any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val outcome = core.runOnce()

        assertEquals(TrafficSamplerCore.Outcome.AllFailed, outcome)
        coVerify(exactly = 0) { historyRepository.commitSample(any(), any(), any()) }
    }

    // ── Single panel success ──────────────────────────────────────────────────

    @Test
    fun `single panel success commits sample with correct panelId and now`() = runTest {
        val singlePanel = panel("solo")
        coEvery { panelRepository.observeAll() } returns flowOf(listOf(singlePanel))

        coEvery {
            xuiClient.fetchInbounds(eq("solo"), any(), any(), any(), any())
        } returns Result.Success(listOf(inbound()))

        core.runOnce()

        coVerify(exactly = 1) {
            historyRepository.commitSample(
                panelId = eq("solo"),
                inbounds = any(),
                sampledAt = eq(clock.nowMs),
            )
        }
    }

    // ── DB commit failure does not flip success count ─────────────────────────

    @Test
    fun `DB commit failure does not count as success — AllFailed if only panel hits DB error`() = runTest {
        coEvery { panelRepository.observeAll() } returns flowOf(listOf(panel()))

        coEvery {
            xuiClient.fetchInbounds(any(), any(), any(), any(), any())
        } returns Result.Success(listOf(inbound()))

        coEvery {
            historyRepository.commitSample(any(), any(), any())
        } returns Result.Failure(DomainError.Unexpected(RuntimeException("db error")))

        val outcome = core.runOnce()

        assertEquals(TrafficSamplerCore.Outcome.AllFailed, outcome)
    }

    // ── Fixed clock helper ────────────────────────────────────────────────────

    private class FixedClock(val nowMs: Long) : SamplerClock {
        override fun nowMillis(): Long = nowMs
    }
}
