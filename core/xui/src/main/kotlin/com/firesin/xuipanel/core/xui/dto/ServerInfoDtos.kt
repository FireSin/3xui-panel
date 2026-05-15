package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /panel/api/server/getXrayVersion
 *
 * Since 3x-ui v26+ this returns the **list of Xray versions available for install**
 * (e.g. `["v26.5.9","v26.4.25"]`), NOT the currently-installed one. The installed
 * version lives in `serverStatus().obj.xray.version` (XrayStatusDto.version).
 */
@Serializable
data class XrayVersionResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<String>? = null,
    @SerialName("msg") val msg: String? = null,
)

/**
 * GET /panel/api/server/getPanelUpdateInfo
 * Returns current/latest panel version and whether an update is available.
 */
@Serializable
data class PanelUpdateInfoDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: PanelUpdateInfoObj? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class PanelUpdateInfoObj(
    @SerialName("currentVersion") val currentVersion: String = "",
    @SerialName("latestVersion") val latestVersion: String = "",
    @SerialName("isUpdatable") val isUpdatable: Boolean = false,
)

/**
 * GET /panel/api/server/getConfigJson
 * Returns the raw Xray config JSON as a string inside the generic envelope.
 * The `obj` field contains the raw JSON value — deserialized as a [kotlinx.serialization.json.JsonElement].
 */
@Serializable
data class ConfigJsonResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("msg") val msg: String? = null,
)
