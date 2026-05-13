package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for POST /panel/api/inbounds/addClient and /panel/api/inbounds/updateClient/:cid — api.txt.
 *
 * `id` is the inbound ID; `settings` is the JSON-encoded `settings.clients` array of that inbound.
 */
@Serializable
data class ClientSettingsBodyDto(
    @SerialName("id") val inboundId: Int,
    @SerialName("settings") val settings: String,
)
