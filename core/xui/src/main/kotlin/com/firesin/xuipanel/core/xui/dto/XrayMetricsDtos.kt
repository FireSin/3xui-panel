package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `GET /panel/api/server/xrayMetricsState`. Shape:
 *  - when not configured: `{enabled:false, listen:"", reason:"metrics block not configured…"}`
 *  - when enabled: `{enabled:true, listen:"…", snapshot:{…}}` — snapshot keys vary by xray build,
 *    so we keep it as a raw [JsonElement].
 */
@Serializable
data class XrayMetricsStateDto(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("listen") val listen: String = "",
    @SerialName("reason") val reason: String? = null,
    @SerialName("snapshot") val snapshot: JsonElement? = null,
)

@Serializable
data class XrayMetricsStateResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: XrayMetricsStateDto? = null,
)

/** One observatory probe entry — populated when xray observatory is configured. */
@Serializable
data class XrayObservatoryEntryDto(
    @SerialName("tag") val tag: String = "",
    @SerialName("alive") val alive: Boolean = false,
    @SerialName("delay") val delay: Long = 0,
    @SerialName("lastSeenTime") val lastSeenTime: Long = 0,
    @SerialName("lastTryTime") val lastTryTime: Long = 0,
)

@Serializable
data class XrayObservatoryResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: List<XrayObservatoryEntryDto>? = null,
)
