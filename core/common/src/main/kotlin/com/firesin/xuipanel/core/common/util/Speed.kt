package com.firesin.xuipanel.core.common.util

import java.util.Locale

private const val KB = 1024L
private const val MB = 1024L * KB
private const val GB = 1024L * MB

/**
 * Formats a bytes-per-second throughput value into a human-readable string
 * with one decimal place, e.g. "12.3 kB/s", "1.4 MB/s", "2.0 GB/s".
 * Uses 1024-byte boundaries and lowercase "kB" convention for kilobytes.
 */
fun formatSpeed(bytesPerSecond: Long): Pair<String, String> {
    if (bytesPerSecond <= 0L) return "0.0" to "kB/s"
    return when {
        bytesPerSecond < MB ->
            String.format(Locale.US, "%.1f", bytesPerSecond.toDouble() / KB) to "kB/s"
        bytesPerSecond < GB ->
            String.format(Locale.US, "%.1f", bytesPerSecond.toDouble() / MB) to "MB/s"
        else ->
            String.format(Locale.US, "%.1f", bytesPerSecond.toDouble() / GB) to "GB/s"
    }
}
