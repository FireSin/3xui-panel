package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for `POST /panel/api/inbounds/updateClientTraffic/:email` — manually adjust client
 * counters. Field names are `upload`/`download` (NOT up/down).
 */
@Serializable
data class UpdateClientTrafficRequestDto(
    @SerialName("upload") val upload: Long,
    @SerialName("download") val download: Long,
)
