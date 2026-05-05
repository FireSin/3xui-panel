package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnlinesResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<String>? = null,
    @SerialName("msg") val msg: String? = null,
)
