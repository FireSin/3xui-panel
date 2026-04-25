package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import java.time.Instant

internal fun PanelEntity.toPanel(): Panel = Panel(
    id = id,
    name = name,
    baseUrl = baseUrl,
    login = login,
    password = password,
    trustSelfSigned = trustSelfSigned != 0,
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
    trustSelfSigned = if (trustSelfSigned) 1 else 0,
    isActive = if (isActive) 1 else 0,
    createdAt = createdAt.toEpochMilli(),
    lastLoginAt = lastLoginAt?.toEpochMilli(),
)
