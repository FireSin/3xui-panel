package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /panel/api/server/xraylogs/:count returns access-log entries as structured records:
 *   {"DateTime":"2025-..","FromAddress":"1.2.3.4:1234","ToAddress":"example.com:443",
 *    "Inbound":"vless-in","Outbound":"direct","Email":"alice","Event":0}
 * where Event is 0=Direct, 1=Blocked, 2=Proxied. XuiClient flattens these into readable lines.
 */
@Serializable
data class XrayLogEntryDto(
    @SerialName("DateTime") val dateTime: String? = null,
    @SerialName("FromAddress") val fromAddress: String? = null,
    @SerialName("ToAddress") val toAddress: String? = null,
    @SerialName("Inbound") val inbound: String? = null,
    @SerialName("Outbound") val outbound: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("Event") val event: Int? = null,
)

@Serializable
data class XrayLogsResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<XrayLogEntryDto>? = null,
    @SerialName("msg") val msg: String? = null,
)
