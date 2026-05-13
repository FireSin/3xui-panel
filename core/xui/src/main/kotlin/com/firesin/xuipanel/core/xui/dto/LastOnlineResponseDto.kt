package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /panel/api/inbounds/lastOnline → `obj` is a map email → last-seen unix timestamp (seconds).
 */
@Serializable
data class LastOnlineResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: Map<String, Long>? = null,
    @SerialName("msg") val msg: String? = null,
)
