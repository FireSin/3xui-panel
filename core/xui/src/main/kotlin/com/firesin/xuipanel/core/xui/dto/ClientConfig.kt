package com.firesin.xuipanel.core.xui.dto

sealed interface ClientConfig {
    val email: String
    val enable: Boolean
    val totalGB: Long
    val expiryTime: Long
    val limitIp: Int
    val subId: String
    val comment: String
    val tgId: String
    val reset: Int
    val createdAt: Long?
    val updatedAt: Long?

    data class Vmess(
        val id: String,
        override val email: String,
        override val enable: Boolean,
        override val totalGB: Long,
        override val expiryTime: Long,
        override val limitIp: Int,
        override val subId: String,
        override val comment: String,
        override val tgId: String = "",
        override val reset: Int = 0,
        override val createdAt: Long? = null,
        override val updatedAt: Long? = null,
    ) : ClientConfig

    data class Vless(
        val id: String,
        val flow: String,
        override val email: String,
        override val enable: Boolean,
        override val totalGB: Long,
        override val expiryTime: Long,
        override val limitIp: Int,
        override val subId: String,
        override val comment: String,
        override val tgId: String = "",
        override val reset: Int = 0,
        override val createdAt: Long? = null,
        override val updatedAt: Long? = null,
    ) : ClientConfig

    data class Shadowsocks(
        val password: String,
        val method: String,
        override val email: String,
        override val enable: Boolean,
        override val totalGB: Long,
        override val expiryTime: Long,
        override val limitIp: Int,
        override val subId: String,
        override val comment: String,
        override val tgId: String = "",
        override val reset: Int = 0,
        override val createdAt: Long? = null,
        override val updatedAt: Long? = null,
    ) : ClientConfig
}

/**
 * Protocol-dependent URL key used in updateClient / deleteClient path segments.
 *
 * Invariant: a Shadowsocks client's `password` is NEVER used as a URL key — its
 * Base64 alphabet contains `/` which would change the request path. SS uses
 * `email`. VMESS/VLESS use the server-assigned UUID.
 */
val ClientConfig.urlKey: String
    get() = when (this) {
        is ClientConfig.Vmess -> id
        is ClientConfig.Vless -> id
        is ClientConfig.Shadowsocks -> email
    }
