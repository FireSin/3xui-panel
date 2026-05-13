package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /panel/api/inbounds/getSubLinks/:subId — every protocol URL (vless://, vmess://, ss://, …)
 * for clients matching the subscription id. Empty array when no enabled clients match.
 */
@Serializable
data class SubLinksResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<String>? = null,
    @SerialName("msg") val msg: String? = null,
)
