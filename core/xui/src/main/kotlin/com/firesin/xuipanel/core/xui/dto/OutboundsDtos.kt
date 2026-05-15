package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row from `GET /panel/xray/getOutboundsTraffic`. */
@Serializable
data class OutboundTrafficDto(
    @SerialName("id") val id: Int,
    @SerialName("tag") val tag: String,
    @SerialName("up") val up: Long,
    @SerialName("down") val down: Long,
    @SerialName("total") val total: Long,
)

@Serializable
data class OutboundsTrafficResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: List<OutboundTrafficDto>? = null,
)

/** `GET /panel/xray/getXrayResult` — last Xray stdout/stderr as a single string. */
@Serializable
data class XrayResultResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: String? = null,
)
