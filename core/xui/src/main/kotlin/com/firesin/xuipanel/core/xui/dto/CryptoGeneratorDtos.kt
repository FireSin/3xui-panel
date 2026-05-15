package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /panel/api/server/getNewmldsa65` — post-quantum signature keypair. */
@Serializable
data class Mldsa65KeypairDto(
    @SerialName("seed") val seed: String,
    @SerialName("verify") val verify: String,
)

@Serializable
data class Mldsa65ResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: Mldsa65KeypairDto? = null,
)

/** `GET /panel/api/server/getNewmlkem768` — post-quantum KEM keypair. Fields are `client`/`server`. */
@Serializable
data class Mlkem768KeypairDto(
    @SerialName("client") val client: String,
    @SerialName("server") val server: String,
)

@Serializable
data class Mlkem768ResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: Mlkem768KeypairDto? = null,
)

/** `GET /panel/api/server/getNewVlessEnc` — multiple auth presets (X25519, mlkem768x25519plus, …). */
@Serializable
data class VlessEncAuthDto(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("encryption") val encryption: String,
    @SerialName("decryption") val decryption: String,
)

@Serializable
data class VlessEncObjDto(
    @SerialName("auths") val auths: List<VlessEncAuthDto>,
)

@Serializable
data class VlessEncResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: VlessEncObjDto? = null,
)

/**
 * `POST /panel/api/server/getNewEchCert` form `sni=…` — ECH (Encrypted Client Hello) keypair.
 * Real panel returns 2 fields (echConfigList + echServerKeys), not 3 as docs imply.
 */
@Serializable
data class EchCertDto(
    @SerialName("echConfigList") val echConfigList: String,
    @SerialName("echServerKeys") val echServerKeys: String,
)

@Serializable
data class EchCertResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: EchCertDto? = null,
)
