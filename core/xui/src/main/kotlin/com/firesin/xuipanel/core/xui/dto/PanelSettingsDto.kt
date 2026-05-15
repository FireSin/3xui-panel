package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /panel/setting/defaultSettings` — surfaces the subscription endpoint URIs the panel
 * is configured to serve, so the app can build full subscription / Clash URLs for each
 * client by appending [ClientConfig.subId] to the relevant base.
 *
 * Only the fields used by the share screen are deserialised. The endpoint actually
 * returns more (datepicker, pageSize, remarkModel, ...) — those are ignored.
 */
@Serializable
data class PanelSettingsDto(
    @SerialName("subEnable") val subEnable: Boolean = false,
    @SerialName("subURI") val subUri: String = "",
    @SerialName("subJsonEnable") val subJsonEnable: Boolean = false,
    @SerialName("subJsonURI") val subJsonUri: String = "",
    @SerialName("subClashEnable") val subClashEnable: Boolean = false,
    @SerialName("subClashURI") val subClashUri: String = "",
)

@Serializable
data class PanelSettingsResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: PanelSettingsDto? = null,
    @SerialName("msg") val msg: String? = null,
)
