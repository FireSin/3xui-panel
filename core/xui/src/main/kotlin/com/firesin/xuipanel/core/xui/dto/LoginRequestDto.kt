package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for POST /login — api.txt.
 * `twoFactorCode` is the OTP for 2FA-enabled panels; omit (null) otherwise.
 */
@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("twoFactorCode") val twoFactorCode: String? = null,
)
