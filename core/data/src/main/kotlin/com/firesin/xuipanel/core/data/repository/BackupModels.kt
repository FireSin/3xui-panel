package com.firesin.xuipanel.core.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val createdAt: String,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val kdfIterations: Int = 100_000,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
)

@Serializable
data class PanelExportDto(
    val id: String,
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val tlsMode: String,
    val pinnedSpkiSha256: String?,
    val pinnedAt: String?,
    val isActive: Boolean,
    val createdAt: String,
    val lastLoginAt: String?,
)
