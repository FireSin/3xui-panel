package com.firesin.xuipanel.core.common.util

private const val MS_PER_DAY = 86_400_000L

sealed class ExpiryLabel {
    data object Never : ExpiryLabel()
    data class ExpiresIn(val days: Int) : ExpiryLabel()
    data class ExpiredAgo(val days: Int) : ExpiryLabel()
}

/**
 * Classifies [expiryMs] relative to [nowMs].
 *
 * - [expiryMs] <= 0  → [ExpiryLabel.Never] (upstream sentinel for "no expiry")
 * - [expiryMs] > [nowMs] → [ExpiryLabel.ExpiresIn], days = floor((expiryMs - nowMs) / 86400000)
 * - [expiryMs] <= [nowMs] → [ExpiryLabel.ExpiredAgo], days = floor((nowMs - expiryMs) / 86400000)
 *
 * Pure function; no Android dependencies, safe to test on the JVM.
 */
fun classifyExpiry(
    expiryMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): ExpiryLabel {
    if (expiryMs <= 0L) return ExpiryLabel.Never
    return if (expiryMs > nowMs) {
        ExpiryLabel.ExpiresIn(((expiryMs - nowMs) / MS_PER_DAY).toInt())
    } else {
        ExpiryLabel.ExpiredAgo(((nowMs - expiryMs) / MS_PER_DAY).toInt())
    }
}
