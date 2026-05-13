package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Single sample on the server-history time series. `t` is a unix-seconds bucket boundary;
 * `v` is the aggregated value (e.g., CPU %, memory bytes, packets/sec depending on metric).
 */
@Serializable
data class ServerHistoryPointDto(
    @SerialName("t") val t: Long,
    @SerialName("v") val v: Double,
)

/**
 * /panel/api/server/history/:metric/:bucket — array of [ServerHistoryPointDto] covering
 * the last ~6 hours. Metrics: cpu | mem | swap | netIn | netOut | tcpCount | udpCount |
 * load1 | online. Buckets in seconds: 2, 30, 60, 120, 180, 300.
 */
@Serializable
data class ServerHistoryResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<ServerHistoryPointDto>? = null,
    @SerialName("msg") val msg: String? = null,
)
