package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /csrf-token` — mints a fresh CSRF token for the current cookie session.
 *
 * Required by every POST under the `panel/setting/` path family because that family
 * isn't covered by the API-token middleware that short-circuits CSRF for `panel/api/`.
 */
@Serializable
data class CsrfTokenResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: String? = null,
    @SerialName("msg") val msg: String? = null,
)
