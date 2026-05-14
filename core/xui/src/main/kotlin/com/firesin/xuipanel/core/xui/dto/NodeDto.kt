package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NodeDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("remark") val remark: String = "",
    @SerialName("scheme") val scheme: String,
    @SerialName("address") val address: String,
    @SerialName("port") val port: Int,
    @SerialName("basePath") val basePath: String = "",
    @SerialName("apiToken") val apiToken: String = "",
    @SerialName("enable") val enable: Boolean = true,
    @SerialName("allowPrivateAddress") val allowPrivateAddress: Boolean = false,
    // runtime-only fields
    @SerialName("status") val status: String = "unknown",
    @SerialName("lastHeartbeat") val lastHeartbeat: Long = 0L,
    @SerialName("latencyMs") val latencyMs: Int = 0,
    @SerialName("xrayVersion") val xrayVersion: String = "",
    @SerialName("cpuPct") val cpuPct: Double = 0.0,
    @SerialName("memPct") val memPct: Double = 0.0,
    @SerialName("uptimeSecs") val uptimeSecs: Long = 0L,
    @SerialName("lastError") val lastError: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

@Serializable
data class NodeListResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<NodeDto>? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class NodeResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: NodeDto? = null,
    @SerialName("msg") val msg: String? = null,
)

/** Body for POST /panel/api/nodes/add and /panel/api/nodes/update/:id */
@Serializable
data class AddNodeRequestDto(
    @SerialName("name") val name: String,
    @SerialName("remark") val remark: String = "",
    @SerialName("scheme") val scheme: String,
    @SerialName("address") val address: String,
    @SerialName("port") val port: Int,
    @SerialName("basePath") val basePath: String = "",
    @SerialName("apiToken") val apiToken: String = "",
    @SerialName("enable") val enable: Boolean = true,
    @SerialName("allowPrivateAddress") val allowPrivateAddress: Boolean = false,
)

/** Body for POST /panel/api/nodes/setEnable/:id */
@Serializable
data class SetNodeEnableRequestDto(
    @SerialName("enable") val enable: Boolean,
)

/** Probe result returned by POST /panel/api/nodes/test */
@Serializable
data class NodeStatusProbeDto(
    @SerialName("status") val status: String = "unknown",
    @SerialName("latencyMs") val latencyMs: Int = 0,
    @SerialName("xrayVersion") val xrayVersion: String = "",
    @SerialName("lastError") val lastError: String = "",
)

@Serializable
data class TestNodeResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: NodeStatusProbeDto? = null,
    @SerialName("msg") val msg: String? = null,
)
