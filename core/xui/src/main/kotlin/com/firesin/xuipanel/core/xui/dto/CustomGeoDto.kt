package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomGeoResourceDto(
    @SerialName("id") val id: Int,
    @SerialName("type") val type: String,
    @SerialName("alias") val alias: String,
    @SerialName("url") val url: String,
    @SerialName("localPath") val localPath: String = "",
    @SerialName("lastUpdatedAt") val lastUpdatedAt: Long = 0L,
    @SerialName("lastModified") val lastModified: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

@Serializable
data class CustomGeoListResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("obj") val obj: List<CustomGeoResourceDto>? = null,
    @SerialName("msg") val msg: String? = null,
)

@Serializable
data class AddCustomGeoRequestDto(
    @SerialName("type") val type: String,
    @SerialName("alias") val alias: String,
    @SerialName("url") val url: String,
)
