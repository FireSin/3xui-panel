package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /panel/api/server/getXrayVersion
 * Returns the currently-installed Xray binary version string, e.g. "v25.5.16".
 */
@Serializable
data class XrayVersionResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: String? = null,
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
