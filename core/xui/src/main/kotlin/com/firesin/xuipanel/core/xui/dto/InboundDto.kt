package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InboundListResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<InboundDto>? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class InboundDto(
    @SerialName("id") val id: Int,
    @SerialName("up") val up: Long,
    @SerialName("down") val down: Long,
    @SerialName("total") val total: Long,
    @SerialName("remark") val remark: String,
    @SerialName("enable") val enable: Boolean,
    @SerialName("expiryTime") val expiryTime: Long,
    @SerialName("clientStats") val clientStats: List<ClientStatDto>? = null,
    @SerialName("listen") val listen: String,
    @SerialName("port") val port: Int,
    @SerialName("protocol") val protocol: String,
    @SerialName("settings") val settings: String,
    @SerialName("streamSettings") val streamSettings: String,
    @SerialName("tag") val tag: String,
    @SerialName("sniffing") val sniffing: String,
)

@Serializable
data class ClientStatDto(
    @SerialName("id") val id: Int,
    @SerialName("inboundId") val inboundId: Int,
    @SerialName("enable") val enable: Boolean,
    @SerialName("email") val email: String,
    @SerialName("up") val up: Long,
    @SerialName("down") val down: Long,
    @SerialName("expiryTime") val expiryTime: Long,
    @SerialName("total") val total: Long,
    @SerialName("reset") val reset: Long,
)
