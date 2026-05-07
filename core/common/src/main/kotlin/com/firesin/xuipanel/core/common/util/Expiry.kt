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

/**
 * Adds [deltaMs] milliseconds to [currentMs].
 * If [currentMs] <= 0 (no-limit sentinel), uses [nowMs] as the base.
 *
 * Pure function; no side effects.
 */
fun addExpiry(
    currentMs: Long,
    deltaMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Long {
    val base = if (currentMs <= 0L) nowMs else currentMs
    return base + deltaMs
}

/**
 * Adds [months] * 30 days to [currentMs] (1 month = exactly 30 days).
 * If [currentMs] <= 0 (no-limit sentinel), uses [nowMs] as the base.
 *
 * Pure function; no side effects.
 */
fun addExpiryMonths(
    currentMs: Long,
    months: Int,
    nowMs: Long = System.currentTimeMillis(),
): Long = addExpiry(currentMs, months * 30L * MS_PER_DAY, nowMs)

private const val MS_PER_HOUR = 3_600_000L
private const val MS_PER_MINUTE = 60_000L

/**
 * Returns "Xд Yч Zм" string (e.g. "3д 22ч 17м") for a positive remaining duration.
 * Returns null when:
 *   - expiryMs == 0L (no limit)
 *   - expiryMs <= nowMs (already expired or exactly now)
 */
fun formatRemainingTime(expiryMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (expiryMs == 0L || expiryMs <= nowMs) return null
    val diff = expiryMs - nowMs
    val days = diff / MS_PER_DAY
    val hours = (diff % MS_PER_DAY) / MS_PER_HOUR
    val minutes = (diff % MS_PER_HOUR) / MS_PER_MINUTE
    return "${days}д ${hours}ч ${minutes}м"
}
