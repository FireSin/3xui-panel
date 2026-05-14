package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for POST /panel/api/inbounds/add and /panel/api/inbounds/update/:id.
 *
 * The three JSON-blob fields ([settings], [streamSettings], [sniffing]) are themselves
 * JSON-encoded strings — 3x-ui stores them verbatim in the database. Use [InboundEncoder]
 * (in feature/inbounds or wherever) to build these from typed structures.
 *
 * api.txt §/panel/api/inbounds/add.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AddInboundRequestDto(
    @SerialName("enable") val enable: Boolean = true,
    @SerialName("remark") val remark: String,
    @SerialName("listen") val listen: String = "",
    @SerialName("port") val port: Int,
    @SerialName("protocol") val protocol: String,
    @SerialName("expiryTime") val expiryTime: Long = 0L,
    @SerialName("total") val total: Long = 0L,
    @SerialName("up") val up: Long = 0L,
    @SerialName("down") val down: Long = 0L,
    @SerialName("settings") val settings: String,
    @SerialName("streamSettings") val streamSettings: String,
    @SerialName("sniffing") val sniffing: String,
    /** Present only for node inbounds; absent (not null) for local panel inbounds. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("nodeId") val nodeId: Int? = null,
)
