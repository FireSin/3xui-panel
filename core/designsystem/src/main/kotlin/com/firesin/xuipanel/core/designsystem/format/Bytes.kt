package com.firesin.xuipanel.core.designsystem.format

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024.0
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return String.format(Locale.US, "%.2f %s", value, units[idx])
}
