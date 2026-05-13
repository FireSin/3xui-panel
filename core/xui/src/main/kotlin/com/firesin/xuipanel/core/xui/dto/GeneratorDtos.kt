package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /panel/api/server/getNewUUID → `obj` is a freshly generated UUIDv4 string. */
@Serializable
data class NewUuidResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: String? = null,
    @SerialName("msg") val msg: String? = null,
)

/** GET /panel/api/server/getNewX25519Cert → `obj` is `{privateKey, publicKey}` for Reality. */
@Serializable
data class NewX25519ResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: X25519KeyPairDto? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class X25519KeyPairDto(
    @SerialName("privateKey") val privateKey: String,
    @SerialName("publicKey") val publicKey: String,
)
