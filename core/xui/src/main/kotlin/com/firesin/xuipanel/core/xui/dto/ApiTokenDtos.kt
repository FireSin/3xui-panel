package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry from `GET /panel/setting/apiTokens`. */
@Serializable
data class ApiTokenDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("token") val token: String,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("createdAt") val createdAt: Long,
)

@Serializable
data class ApiTokensListResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: List<ApiTokenDto>? = null,
)

/** Body for `POST /panel/setting/apiTokens/create`. */
@Serializable
data class CreateApiTokenRequestDto(
    @SerialName("name") val name: String,
)

@Serializable
data class CreateApiTokenResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: ApiTokenDto? = null,
)

/** Body for `POST /panel/setting/apiTokens/setEnabled/:id`. */
@Serializable
data class SetApiTokenEnabledRequestDto(
    @SerialName("enabled") val enabled: Boolean,
)
