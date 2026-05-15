package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /panel/xray/` — Xray config template + tag lists.
 *
 * NOTE: `xraySetting` is a raw JSON *string* (escaped), not a nested object. The other tag
 * fields look like raw strings on the wire too but are actually JSON-encoded arrays.
 */
@Serializable
data class XrayTemplateObjDto(
    @SerialName("xraySetting") val xraySetting: String = "",
    @SerialName("inboundTags") val inboundTags: String = "",
    @SerialName("clientReverseTags") val clientReverseTags: String = "",
    @SerialName("outboundTestUrl") val outboundTestUrl: String = "",
)

@Serializable
data class XrayTemplateResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: XrayTemplateObjDto? = null,
)

/** `POST /panel/xray/testOutbound` — result inside outer envelope. */
@Serializable
data class TestOutboundResultDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("delay") val delay: Long = 0,
    @SerialName("error") val error: String = "",
    @SerialName("mode") val mode: String = "",
)

@Serializable
data class TestOutboundResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: TestOutboundResultDto? = null,
)
