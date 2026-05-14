package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /panel/api/getTwoFactorEnable — open endpoint, callable before login. */
@Serializable
data class TwoFactorResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: Boolean? = null,
    @SerialName("msg") val msg: String? = null,
)
