package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Traffic counters for a single client.
 *
 * Corresponds to the `ClientTraffics` struct in 3x-ui database/model/model.go.
 * Both endpoints share this shape:
 *   GET /panel/api/inbounds/getClientTraffics/{email}
 *   GET /panel/api/inbounds/getClientTrafficsById/{id}
 *
 * - [up] / [down] — bytes uploaded/downloaded since last reset.
 * - [total] — traffic cap in bytes (0 = unlimited).
 * - [expiryTime] — Unix millis; 0 means no expiry.
 * - [reset] — number of times the counters have been reset.
 */
@Serializable
data class ClientTrafficDto(
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

@Serializable
data class ClientTrafficResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: ClientTrafficDto? = null,
    @SerialName("msg") val msg: String? = null,
)
