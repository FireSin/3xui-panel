package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /panel/api/inbounds/getClientLinks/:id/:email — server-rendered share URLs for one client.
 * Empty [obj] for protocols without a URL form (socks/http/mixed/wireguard/dokodemo/tunnel).
 * Multiple entries when `streamSettings.externalProxy` is configured.
 */
@Serializable
data class ClientLinksResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<String>? = null,
    @SerialName("msg") val msg: String? = null,
)
