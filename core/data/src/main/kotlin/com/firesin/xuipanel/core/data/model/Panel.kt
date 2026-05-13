package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.TlsMode
import java.time.Instant

data class Panel(
    val id: String,
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val tlsMode: TlsMode,
    val pinnedSpkiSha256: String?,
    val pinnedAt: Instant?,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    val apiToken: String? = null,
)
