package com.firesin.xuipanel.core.sampler

import com.firesin.xuipanel.core.xui.dto.InboundDto

/**
 * Pure-JVM, stateless calculator that turns a snapshot of current cumulative counters
 * (from the 3x-ui API) and the previously stored state into per-scope traffic deltas.
 *
 * No Android dependencies — fully testable without instrumentation.
 */
object TrafficDeltaCalculator {

    /** Identifies a traffic scope: either an inbound or a client. */
    data class ScopeId(val kind: ScopeKind, val key: String)

    enum class ScopeKind { INBOUND, CLIENT }

    /** The previously stored cumulative state for a scope. */
    data class StateRow(val lastUp: Long, val lastDown: Long)

    /** A single computed delta for a scope. */
    data class ScopeDelta(
        val scopeId: ScopeId,
        val inboundId: Int?,
        val upDelta: Long,
        val downDelta: Long,
        /** Updated cumulative to be persisted back to traffic_state */
        val newUp: Long,
        val newDown: Long,
    )

    /** The full result of one [compute] call. */
    data class Sample(
        val deltas: List<ScopeDelta>,
        val sampledAt: Long,
        val dayEpoch: Long,
    )

    /**
     * Computes traffic deltas for all inbounds and their clients.
     *
     * @param prev Map of previously stored state, keyed by [ScopeId]. Empty/null for the first run.
     * @param inbounds Current inbound list from the API.
     * @param now Epoch millis of the current sample.
     * @param day UTC-midnight epoch millis for the current day bucket.
     */
    fun compute(
        prev: Map<ScopeId, StateRow>,
        inbounds: List<InboundDto>,
        now: Long,
        day: Long,
    ): Sample {
        val deltas = mutableListOf<ScopeDelta>()

        for (inbound in inbounds) {
            val iId = ScopeId(ScopeKind.INBOUND, inbound.id.toString())
            val pInbound = prev[iId]
            val (dUp, dDown) = delta(inbound.up, inbound.down, pInbound)
            deltas += ScopeDelta(
                scopeId = iId,
                inboundId = null,
                upDelta = dUp,
                downDelta = dDown,
                newUp = inbound.up,
                newDown = inbound.down,
            )

            for (client in inbound.clientStats.orEmpty()) {
                val cId = ScopeId(ScopeKind.CLIENT, client.email)
                val pClient = prev[cId]
                val (cdUp, cdDown) = delta(client.up, client.down, pClient)
                deltas += ScopeDelta(
                    scopeId = cId,
                    inboundId = client.inboundId,
                    upDelta = cdUp,
                    downDelta = cdDown,
                    newUp = client.up,
                    newDown = client.down,
                )
            }
        }

        return Sample(deltas = deltas, sampledAt = now, dayEpoch = day)
    }

    /**
     * Delta rules (§4 design doc):
     * - No previous state → delta = 0 (first-sample baseline).
     * - Counter reset (cur < prev) → delta = cur (best approximation).
     * - Normal → delta = cur - prev.
     */
    private fun delta(curUp: Long, curDown: Long, prev: StateRow?): Pair<Long, Long> {
        if (prev == null) return 0L to 0L
        val dUp = if (curUp < prev.lastUp) curUp else curUp - prev.lastUp
        val dDown = if (curDown < prev.lastDown) curDown else curDown - prev.lastDown
        return dUp to dDown
    }
}
