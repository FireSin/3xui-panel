package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for POST /panel/api/inbounds/:id/copyClients — api.txt lines 164–172.
 *
 * [targetInboundId] must match the `:id` path parameter.
 * [clientEmails] empty list means copy all clients.
 * [flow] empty string means preserve each client's original flow.
 */
@Serializable
data class CopyClientsRequestDto(
    @SerialName("id") val targetInboundId: Int,
    @SerialName("sourceInboundId") val sourceInboundId: Int,
    @SerialName("clientEmails") val clientEmails: List<String>,
    @SerialName("flow") val flow: String,
)
