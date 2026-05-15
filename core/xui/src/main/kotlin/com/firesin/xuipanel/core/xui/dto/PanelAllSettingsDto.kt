package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `POST /panel/setting/all` — returns the full settings blob (~70 fields, including TG-bot,
 * subscription, LDAP, web/cert). Kept as a raw `JsonObject` so we can update only the fields
 * we know about and round-trip the rest unchanged.
 *
 * NOTE: under `/panel/setting/` — cookie session + `X-CSRF-Token` required.
 */
@Serializable
data class PanelAllSettingsResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: JsonObject? = null,
)

/** Body for `POST /panel/setting/updateUser` — rotates admin credentials. */
@Serializable
data class UpdateUserRequestDto(
    @SerialName("oldUsername") val oldUsername: String,
    @SerialName("oldPassword") val oldPassword: String,
    @SerialName("newUsername") val newUsername: String,
    @SerialName("newPassword") val newPassword: String,
)
