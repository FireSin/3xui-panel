package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `POST /panel/xray/` — Xray config template + tag lists.
 *
 * Wire shape: `obj` is a JSON-encoded **string** containing the whole object — not a nested
 * JSON object. After decoding that outer string, [xraySetting] is itself a structured
 * Xray config tree (log/api/inbounds/outbounds/policy/routing/stats), so we keep it as a
 * raw [JsonObject] and let callers traverse/mutate it directly.
 */
@Serializable
data class XrayTemplateObjDto(
    @SerialName("xraySetting") val xraySetting: JsonObject = JsonObject(emptyMap()),
    @SerialName("inboundTags") val inboundTags: List<String> = emptyList(),
    @SerialName("clientReverseTags") val clientReverseTags: List<String> = emptyList(),
    @SerialName("outboundTestUrl") val outboundTestUrl: String = "",
)

@Serializable
data class XrayTemplateResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: String? = null,
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
