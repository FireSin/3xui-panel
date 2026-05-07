package com.firesin.xuipanel.core.sampler

/**
 * Minimal clock abstraction for [TrafficSamplerCore] — allows deterministic time in tests.
 */
interface SamplerClock {
    fun nowMillis(): Long
}

/** Production implementation backed by [System.currentTimeMillis]. */
object SystemSamplerClock : SamplerClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
