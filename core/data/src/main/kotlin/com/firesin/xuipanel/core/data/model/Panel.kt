package com.firesin.xuipanel.core.data.model

import java.time.Instant

data class Panel(
    val id: String,
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val trustSelfSigned: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
)
