package com.firesin.xuipanel.core.common.util

import java.util.Locale

private const val KB = 1024L
private const val MB = 1024L * KB
private const val GB = 1024L * MB
private const val TB = 1024L * GB

/**
 * Formats [bytes] as a human-readable string with one decimal place.
 * Uses B / KB / MB / GB / TB thresholds at 1024 boundaries.
 * Negative or zero values → "0 B".
 * Decimal separator is always a period (US locale, technical admin format).
 */
fun prettyBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < KB -> "$bytes B"
        bytes < MB -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / KB)
        bytes < GB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / MB)
        bytes < TB -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / GB)
        else       -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / TB)
    }
}
