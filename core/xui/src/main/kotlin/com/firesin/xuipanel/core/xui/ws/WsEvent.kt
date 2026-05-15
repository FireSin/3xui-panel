package com.firesin.xuipanel.core.xui.ws

import com.firesin.xuipanel.core.xui.dto.ServerStatusDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Live event pushed by the panel over `/ws`. The wire envelope is `{type, payload}` (or
 * `{type, …flat fields}` for some events).
 *
 * NB: real panel also pushes `type:traffic` (per-client up/down/total snapshot every 2s),
 * which the docs don't mention. We capture it as a typed event.
 */
sealed class WsEvent {
    /** Server-wide status snapshot (CPU/RAM/disk/net/xray.state). Pushed every 2s. */
    data class Status(val server: ServerStatusDto, val timeMs: Long) : WsEvent()

    /** Per-client traffic snapshot. */
    data class Traffic(val clientTraffics: List<ClientTrafficSnapshot>) : WsEvent()

    /** Xray process state changed: "running"/"stopped"/error. */
    data class XrayState(val state: String) : WsEvent()

    /** In-panel toast — title/body/severity. */
    data class Notification(
        val title: String,
        val body: String,
        val severity: String,
    ) : WsEvent()

    /** Re-fetch a resource on the next interaction; e.g. resource="inbounds". */
    data class Invalidate(val resource: String) : WsEvent()

    /** Event whose `type` we don't model yet. Caller can inspect the raw JSON. */
    data class Unknown(val type: String, val rawJson: String) : WsEvent()
}

@Serializable
data class ClientTrafficSnapshot(
    @SerialName("id") val id: Int = 0,
    @SerialName("inboundId") val inboundId: Int = 0,
    @SerialName("enable") val enable: Boolean = false,
    @SerialName("email") val email: String = "",
    @SerialName("uuid") val uuid: String = "",
    @SerialName("subId") val subId: String = "",
    @SerialName("up") val up: Long = 0,
    @SerialName("down") val down: Long = 0,
    @SerialName("allTime") val allTime: Long = 0,
    @SerialName("expiryTime") val expiryTime: Long = 0,
    @SerialName("total") val total: Long = 0,
    @SerialName("reset") val reset: Long = 0,
    @SerialName("lastOnline") val lastOnline: Long = 0,
)
