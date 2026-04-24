package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerStatusResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: ServerStatusDto? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class ServerStatusDto(
    @SerialName("cpu") val cpu: Double,
    @SerialName("mem") val mem: MemDto,
    @SerialName("xray") val xray: XrayStatusDto,
    @SerialName("uptime") val uptime: Long,
    @SerialName("loads") val loads: List<Double>,
    @SerialName("tcpCount") val tcpCount: Int,
    @SerialName("udpCount") val udpCount: Int,
    @SerialName("netIO") val netIO: NetIoDto,
    @SerialName("netTraffic") val netTraffic: NetTrafficDto,
    @SerialName("publicIP") val publicIP: PublicIpDto,
    @SerialName("appStats") val appStats: AppStatsDto? = null,
)

@Serializable
data class MemDto(
    @SerialName("current") val current: Long,
    @SerialName("total") val total: Long,
)

@Serializable
data class XrayStatusDto(
    @SerialName("state") val state: String,
    @SerialName("errorMsg") val errorMsg: String,
    @SerialName("version") val version: String,
)

@Serializable
data class NetIoDto(
    @SerialName("up") val up: Long,
    @SerialName("down") val down: Long,
)

@Serializable
data class NetTrafficDto(
    @SerialName("sent") val sent: Long,
    @SerialName("recv") val recv: Long,
)

@Serializable
data class PublicIpDto(
    @SerialName("ipv4") val ipv4: String,
    @SerialName("ipv6") val ipv6: String,
)

@Serializable
data class AppStatsDto(
    @SerialName("threads") val threads: Int,
    @SerialName("mem") val mem: Long,
)
