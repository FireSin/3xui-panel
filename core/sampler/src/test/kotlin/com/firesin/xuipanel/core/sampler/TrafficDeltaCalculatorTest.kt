package com.firesin.xuipanel.core.sampler

import com.firesin.xuipanel.core.sampler.TrafficDeltaCalculator.ScopeId
import com.firesin.xuipanel.core.sampler.TrafficDeltaCalculator.ScopeKind
import com.firesin.xuipanel.core.sampler.TrafficDeltaCalculator.StateRow
import com.firesin.xuipanel.core.xui.dto.ClientStatDto
import com.firesin.xuipanel.core.xui.dto.InboundDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TrafficDeltaCalculatorTest {

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private val now = 1_700_000_000_000L
    private val day = 1_699_920_000_000L // UTC midnight

    private fun inbound(
        id: Int = 1,
        up: Long = 0L,
        down: Long = 0L,
        clients: List<ClientStatDto> = emptyList(),
    ) = InboundDto(
        id = id,
        up = up,
        down = down,
        total = 0L,
        remark = "test",
        enable = true,
        expiryTime = 0L,
        clientStats = clients,
        listen = "",
        port = 443,
        protocol = "vless",
        settings = "{}",
        streamSettings = "{}",
        tag = "tag-$id",
        sniffing = "{}",
    )

    private fun client(
        inboundId: Int = 1,
        email: String = "alice",
        up: Long = 0L,
        down: Long = 0L,
    ) = ClientStatDto(
        id = 0,
        inboundId = inboundId,
        enable = true,
        email = email,
        up = up,
        down = down,
        expiryTime = 0L,
        total = 0L,
        reset = 0L,
    )

    // ── §4: first sample (prev == null) → delta = 0 ───────────────────────────

    @Test
    fun `first sample for inbound returns zero delta and stores current as newUp newDown`() {
        val sample = TrafficDeltaCalculator.compute(
            prev = emptyMap(),
            inbounds = listOf(inbound(id = 1, up = 500L, down = 1000L)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(0L, d.upDelta, "first sample: upDelta must be 0")
        assertEquals(0L, d.downDelta, "first sample: downDelta must be 0")
        assertEquals(500L, d.newUp)
        assertEquals(1000L, d.newDown)
    }

    @Test
    fun `first sample for client returns zero delta`() {
        val sample = TrafficDeltaCalculator.compute(
            prev = emptyMap(),
            inbounds = listOf(inbound(clients = listOf(client(email = "bob", up = 200L, down = 400L)))),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.CLIENT }
        assertEquals(0L, d.upDelta)
        assertEquals(0L, d.downDelta)
        assertEquals(200L, d.newUp)
        assertEquals(400L, d.newDown)
    }

    // ── §4: normal increment (cur >= prev) → delta = cur - prev ───────────────

    @Test
    fun `normal inbound delta is cur minus prev`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(lastUp = 100L, lastDown = 200L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, up = 350L, down = 700L)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(250L, d.upDelta)
        assertEquals(500L, d.downDelta)
    }

    @Test
    fun `normal client delta is cur minus prev`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(lastUp = 0L, lastDown = 0L),
            ScopeId(ScopeKind.CLIENT, "alice") to StateRow(lastUp = 50L, lastDown = 80L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(clients = listOf(client(email = "alice", up = 150L, down = 280L)))),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId == ScopeId(ScopeKind.CLIENT, "alice") }
        assertEquals(100L, d.upDelta)
        assertEquals(200L, d.downDelta)
    }

    // ── §4: counter reset (cur < prev) → delta = cur ─────────────────────────

    @Test
    fun `counter reset on inbound up uses cur as delta`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(lastUp = 1000L, lastDown = 500L),
        )

        // up was reset, down is normal
        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, up = 50L, down = 600L)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(50L, d.upDelta, "reset up: delta = cur")
        assertEquals(100L, d.downDelta, "normal down: delta = cur - prev")
    }

    @Test
    fun `counter reset on inbound down uses cur as delta`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(lastUp = 200L, lastDown = 2000L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, up = 300L, down = 10L)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(100L, d.upDelta, "normal up: delta = cur - prev")
        assertEquals(10L, d.downDelta, "reset down: delta = cur")
    }

    @Test
    fun `full counter reset on client yields cur as both deltas`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(0L, 0L),
            ScopeId(ScopeKind.CLIENT, "carol") to StateRow(lastUp = 999L, lastDown = 999L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(clients = listOf(client(email = "carol", up = 5L, down = 3L)))),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.CLIENT }
        assertEquals(5L, d.upDelta)
        assertEquals(3L, d.downDelta)
    }

    // ── Multiple inbounds / clients ───────────────────────────────────────────

    @Test
    fun `multiple inbounds produce independent deltas`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(100L, 200L),
            ScopeId(ScopeKind.INBOUND, "2") to StateRow(300L, 400L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(
                inbound(id = 1, up = 150L, down = 250L),
                inbound(id = 2, up = 500L, down = 900L),
            ),
            now = now,
            day = day,
        )

        val d1 = sample.deltas.single { it.scopeId == ScopeId(ScopeKind.INBOUND, "1") }
        val d2 = sample.deltas.single { it.scopeId == ScopeId(ScopeKind.INBOUND, "2") }
        assertEquals(50L, d1.upDelta)
        assertEquals(50L, d1.downDelta)
        assertEquals(200L, d2.upDelta)
        assertEquals(500L, d2.downDelta)
    }

    // ── Disappeared client (prev exists, cur absent) ──────────────────────────

    @Test
    fun `client disappeared from API is absent from deltas`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(0L, 0L),
            ScopeId(ScopeKind.CLIENT, "gone") to StateRow(100L, 200L),
        )

        // "gone" client not in this tick's inbound
        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, clients = emptyList())),
            now = now,
            day = day,
        )

        val clientDeltas = sample.deltas.filter { it.scopeId.kind == ScopeKind.CLIENT }
        assertEquals(0, clientDeltas.size, "disappeared client must not produce a delta")
    }

    // ── Sample metadata ───────────────────────────────────────────────────────

    @Test
    fun `sample carries correct now and dayEpoch`() {
        val sample = TrafficDeltaCalculator.compute(
            prev = emptyMap(),
            inbounds = listOf(inbound()),
            now = now,
            day = day,
        )
        assertEquals(now, sample.sampledAt)
        assertEquals(day, sample.dayEpoch)
    }

    // ── Zero-delta edge: cur == prev ──────────────────────────────────────────

    @Test
    fun `no traffic change yields zero delta`() {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(100L, 200L),
        )

        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, up = 100L, down = 200L)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(0L, d.upDelta)
        assertEquals(0L, d.downDelta)
    }

    // ── inboundId carried correctly for CLIENT ────────────────────────────────

    @Test
    fun `client delta carries correct inboundId`() {
        val sample = TrafficDeltaCalculator.compute(
            prev = emptyMap(),
            inbounds = listOf(inbound(id = 42, clients = listOf(client(inboundId = 42, email = "dave")))),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.CLIENT }
        assertEquals(42, d.inboundId)
    }

    // ── INBOUND scope never has inboundId ─────────────────────────────────────

    @Test
    fun `inbound delta has null inboundId`() {
        val sample = TrafficDeltaCalculator.compute(
            prev = emptyMap(),
            inbounds = listOf(inbound(id = 7)),
            now = now,
            day = day,
        )

        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(null, d.inboundId)
    }

    // ── Parameterized table for delta boundary values ─────────────────────────

    @ParameterizedTest(name = "prev=({0},{1}) cur=({2},{3}) → delta=({4},{5})")
    @MethodSource("deltaTable")
    fun `delta computation table`(
        prevUp: Long, prevDown: Long,
        curUp: Long, curDown: Long,
        expectedDeltaUp: Long, expectedDeltaDown: Long,
    ) {
        val prevState = mapOf(
            ScopeId(ScopeKind.INBOUND, "1") to StateRow(prevUp, prevDown),
        )
        val sample = TrafficDeltaCalculator.compute(
            prev = prevState,
            inbounds = listOf(inbound(id = 1, up = curUp, down = curDown)),
            now = now,
            day = day,
        )
        val d = sample.deltas.single { it.scopeId.kind == ScopeKind.INBOUND }
        assertEquals(expectedDeltaUp, d.upDelta, "upDelta")
        assertEquals(expectedDeltaDown, d.downDelta, "downDelta")
    }

    companion object {
        @JvmStatic
        fun deltaTable() = listOf(
            // prevUp, prevDown, curUp, curDown, expectedUp, expectedDown
            arrayOf(0L, 0L, 100L, 200L, 100L, 200L),        // normal from zero
            arrayOf(100L, 200L, 100L, 200L, 0L, 0L),         // no change
            arrayOf(100L, 200L, 500L, 800L, 400L, 600L),     // normal increment
            arrayOf(1000L, 2000L, 10L, 20L, 10L, 20L),       // full reset both
            arrayOf(500L, 100L, 200L, 300L, 200L, 200L),     // reset up only, normal down
            arrayOf(100L, 500L, 300L, 200L, 200L, 200L),     // normal up, reset down
            arrayOf(0L, 0L, 0L, 0L, 0L, 0L),                 // all zeros
            arrayOf(Long.MAX_VALUE / 2, 0L, 0L, 0L, 0L, 0L), // reset from huge value
        )
    }
}
