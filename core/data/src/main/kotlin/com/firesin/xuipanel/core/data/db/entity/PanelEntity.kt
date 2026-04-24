package com.firesin.xuipanel.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "panels")
data class PanelEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    @ColumnInfo(name = "login")
    val login: String,

    @ColumnInfo(name = "password")
    val password: String,

    /** 0 = strict TLS, 1 = accept self-signed. */
    @ColumnInfo(name = "trust_self_signed")
    val trustSelfSigned: Int,

    /** Only one row may have is_active = 1; enforced in the repository. */
    @ColumnInfo(name = "is_active")
    val isActive: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long?,
)
