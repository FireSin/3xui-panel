package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /panel/xray/warp/:action` and `/panel/xray/nord/:action` — generic envelope.
 *
 * The panel returns `obj` as a *string* for several actions (escaped JSON for `data`/`config`).
 * For actions like `del`, `reg`, `license`, `setKey`, `obj` is typically empty/null.
 */
@Serializable
data class WarpNordResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("msg") val msg: String? = null,
    @SerialName("obj") val obj: String? = null,
)
