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

    /** Kept for migration compatibility — no longer read after schema v2. */
    @ColumnInfo(name = "trust_self_signed")
    val trustSelfSigned: Int,

    /** Only one row may have is_active = 1; enforced in the repository. */
    @ColumnInfo(name = "is_active")
    val isActive: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long?,

    /** "SYSTEM" or "PINNED". Defaults to "SYSTEM" for new rows. */
    @ColumnInfo(name = "tls_mode", defaultValue = "SYSTEM")
    val tlsMode: String,

    /** Base64 SHA-256 of the leaf certificate SPKI. Null when tlsMode = SYSTEM or pin not yet captured. */
    @ColumnInfo(name = "pinned_spki_sha256")
    val pinnedSpkiSha256: String?,

    /** Epoch millis when the pin was last set. */
    @ColumnInfo(name = "pinned_at")
    val pinnedAt: Long?,

    /** Bearer API token for the panel. When non-null, used instead of login/password. */
    @ColumnInfo(name = "api_token")
    val apiToken: String?,
)
