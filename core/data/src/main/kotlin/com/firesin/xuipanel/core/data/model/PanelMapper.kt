package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import java.time.Instant

internal fun PanelEntity.toPanel(): Panel = Panel(
    id = id,
    name = name,
    baseUrl = baseUrl,
    login = login,
    password = password,
    tlsMode = tlsModeFromString(tlsMode),
    pinnedSpkiSha256 = pinnedSpkiSha256,
    pinnedAt = pinnedAt?.let { Instant.ofEpochMilli(it) },
    isActive = isActive != 0,
    createdAt = Instant.ofEpochMilli(createdAt),
    lastLoginAt = lastLoginAt?.let { Instant.ofEpochMilli(it) },
)

internal fun Panel.toEntity(): PanelEntity = PanelEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    login = login,
    password = password,
    trustSelfSigned = if (tlsMode == TlsMode.PINNED) 1 else 0,
    isActive = if (isActive) 1 else 0,
    createdAt = createdAt.toEpochMilli(),
    lastLoginAt = lastLoginAt?.toEpochMilli(),
    tlsMode = tlsMode.name,
    pinnedSpkiSha256 = pinnedSpkiSha256,
    pinnedAt = pinnedAt?.toEpochMilli(),
)

private fun tlsModeFromString(value: String): TlsMode =
    TlsMode.entries.firstOrNull { it.name == value } ?: TlsMode.SYSTEM
