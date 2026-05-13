package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body for POST /panel/api/inbounds/setEnable/:id — api.txt. */
@Serializable
data class SetEnableRequestDto(
    @SerialName("enable") val enable: Boolean,
)
