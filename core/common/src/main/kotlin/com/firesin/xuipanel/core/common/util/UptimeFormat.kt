package com.firesin.xuipanel.core.common.util

/**
 * Formats [seconds] into a compact uptime string.
 * ≥1 day  → "Xd Yh"
 * <1 day  → "Xh Ym"
 * <1 hour → "Xm"
 */
fun secondsToCompact(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
