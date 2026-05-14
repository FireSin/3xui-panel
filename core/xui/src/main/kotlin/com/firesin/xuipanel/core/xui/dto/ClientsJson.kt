package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.longOrNull

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

object ClientsJson {

    /** Parses the inbound `settings` JSON string and returns the clients list for the given protocol. */
    fun parse(protocol: String, settingsJson: String): List<ClientConfig> {
        val root = json.parseToJsonElement(settingsJson).jsonObject
        val clientsArray = root["clients"]?.jsonArray ?: return emptyList()
        return clientsArray.map { element ->
            val obj = element.jsonObject
            parseClient(protocol, obj)
        }
    }

    /**
     * Encodes a single client into the `settings` JSON body required by addClient / updateClient.
     * Shape: `{"clients":[{...}]}`
     */
    fun encodeSettingsBody(client: ClientConfig): String {
        val clientJson = encodeClient(client)
        val wrapper = buildJsonObject {
            put("clients", buildJsonArray { add(clientJson) })
        }
        return json.encodeToString(JsonObject.serializer(), wrapper)
    }

    /**
     * Extracts the top-level `password` field from a Shadowsocks inbound `settings` JSON.
     *
     * Required for SS-2022 cipher userinfo: `method:inboundPassword:clientPassword`.
     * Returns null if the field is absent or blank.
     *
     * upstream: sub/subService.go genShadowsocksLink reads settings["password"] @main
     */
    fun parseInboundPassword(settingsJson: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(settingsJson).jsonObject
            root["password"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parseClient(protocol: String, obj: JsonObject): ClientConfig {
        val email = obj["email"]?.jsonPrimitive?.content.orEmpty()
        val enable = obj["enable"]?.jsonPrimitive?.boolean ?: true
        val totalGB = obj["totalGB"]?.jsonPrimitive?.long ?: 0L
        val expiryTime = obj["expiryTime"]?.jsonPrimitive?.long ?: 0L
        val limitIp = obj["limitIp"]?.jsonPrimitive?.int ?: 0
        val subId = obj["subId"]?.jsonPrimitive?.content.orEmpty()
        val comment = obj["comment"]?.jsonPrimitive?.content.orEmpty()
        val tgId = obj["tgId"]?.jsonPrimitive?.content.orEmpty()
        val reset = obj["reset"]?.jsonPrimitive?.int ?: 0
        val createdAt = obj["created_at"]?.jsonPrimitive?.longOrNull
        val updatedAt = obj["updated_at"]?.jsonPrimitive?.longOrNull

        return when (protocol.lowercase()) {
            "vmess" -> ClientConfig.Vmess(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                email = email,
                enable = enable,
                totalGB = totalGB,
                expiryTime = expiryTime,
                limitIp = limitIp,
                subId = subId,
                comment = comment,
                tgId = tgId,
                reset = reset,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            "vless" -> ClientConfig.Vless(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                flow = obj["flow"]?.jsonPrimitive?.content.orEmpty(),
                email = email,
                enable = enable,
                totalGB = totalGB,
                expiryTime = expiryTime,
                limitIp = limitIp,
                subId = subId,
                comment = comment,
                tgId = tgId,
                reset = reset,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            "shadowsocks" -> ClientConfig.Shadowsocks(
                password = obj["password"]?.jsonPrimitive?.content.orEmpty(),
                method = obj["method"]?.jsonPrimitive?.content.orEmpty(),
                email = email,
                enable = enable,
                totalGB = totalGB,
                expiryTime = expiryTime,
                limitIp = limitIp,
                subId = subId,
                comment = comment,
                tgId = tgId,
                reset = reset,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            "trojan" -> ClientConfig.Trojan(
                password = obj["password"]?.jsonPrimitive?.content.orEmpty(),
                flow = obj["flow"]?.jsonPrimitive?.content.orEmpty(),
                email = email,
                enable = enable,
                totalGB = totalGB,
                expiryTime = expiryTime,
                limitIp = limitIp,
                subId = subId,
                comment = comment,
                tgId = tgId,
                reset = reset,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            "hysteria" -> ClientConfig.Hysteria(
                auth = obj["auth"]?.jsonPrimitive?.content.orEmpty(),
                email = email,
                enable = enable,
                totalGB = totalGB,
                expiryTime = expiryTime,
                limitIp = limitIp,
                subId = subId,
                comment = comment,
                tgId = tgId,
                reset = reset,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            else -> error("Unsupported protocol for client parsing: $protocol")
        }
    }

    private fun encodeClient(client: ClientConfig): JsonObject = buildJsonObject {
        when (client) {
            is ClientConfig.Vmess -> {
                put("id", JsonPrimitive(client.id))
            }
            is ClientConfig.Vless -> {
                put("id", JsonPrimitive(client.id))
                put("flow", JsonPrimitive(client.flow))
            }
            is ClientConfig.Shadowsocks -> {
                put("password", JsonPrimitive(client.password))
                put("method", JsonPrimitive(client.method))
            }
            is ClientConfig.Trojan -> {
                put("password", JsonPrimitive(client.password))
                put("flow", JsonPrimitive(client.flow))
            }
            is ClientConfig.Hysteria -> {
                put("auth", JsonPrimitive(client.auth))
            }
        }
        put("email", JsonPrimitive(client.email))
        put("limitIp", JsonPrimitive(client.limitIp))
        put("totalGB", JsonPrimitive(client.totalGB))
        put("expiryTime", JsonPrimitive(client.expiryTime))
        put("enable", JsonPrimitive(client.enable))
        put("tgId", JsonPrimitive(client.tgId))
        put("subId", JsonPrimitive(client.subId))
        put("comment", JsonPrimitive(client.comment))
        put("reset", JsonPrimitive(client.reset))
        if (client.createdAt != null) {
            put("created_at", JsonPrimitive(client.createdAt))
        }
        if (client.updatedAt != null) {
            put("updated_at", JsonPrimitive(client.updatedAt))
        }
    }
}
