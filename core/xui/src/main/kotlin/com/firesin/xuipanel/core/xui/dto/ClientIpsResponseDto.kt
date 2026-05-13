package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * /panel/api/inbounds/clientIps/:email returns an [obj] that is either:
 * - a `List<String>` when records exist, or
 * - a string `"No IP Record"` when there are none.
 *
 * The raw [obj] is kept as [JsonElement]; XuiClient flattens it into a [List].
 */
@Serializable
data class ClientIpsResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: JsonElement? = null,
    @SerialName("msg") val msg: String? = null,
)
