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

    data class Trojan(
        val password: String,
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

    data class Hysteria(
        val auth: String,
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
 * Invariant: Shadowsocks, Trojan, and Hysteria secrets are NEVER used as URL keys —
 * Base64/special characters in passwords/auth strings would corrupt the request path.
 * Those protocols use `email`. VMESS/VLESS use the server-assigned UUID.
 */
val ClientConfig.urlKey: String
    get() = when (this) {
        is ClientConfig.Vmess -> id
        is ClientConfig.Vless -> id
        is ClientConfig.Shadowsocks -> email
        is ClientConfig.Trojan -> email
        is ClientConfig.Hysteria -> email
    }
