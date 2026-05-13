package com.firesin.xuipanel.core.xui.draft

import com.firesin.xuipanel.core.xui.dto.AddInboundRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Converts a typed [InboundDraft] into the three JSON-stringified blobs that
 * `POST /panel/api/inbounds/add` and `/update/:id` expect.
 *
 * The shapes mirror what 3x-ui's `InboundService.saveInbound` writes to the DB —
 * see web/service/inbound.go in upstream. Keep field names/cases verbatim so a draft
 * encoded here round-trips identically to one created in the web UI.
 */
object InboundEncoder {

    private val json = Json { encodeDefaults = true }

    fun encode(draft: InboundDraft): AddInboundRequestDto = AddInboundRequestDto(
        enable = draft.enable,
        remark = draft.remark,
        listen = draft.listen,
        port = draft.port,
        protocol = draft.protocol.protocolName,
        expiryTime = draft.expiryTime,
        total = draft.total,
        settings = encodeSettings(draft.protocol),
        streamSettings = encodeStreamSettings(draft.stream),
        sniffing = encodeSniffing(draft.sniffing),
    )

    // ---- settings ----

    private fun encodeSettings(p: ProtocolSettings): String = when (p) {
        is ProtocolSettings.Vless -> buildJsonObject {
            putClients(p.clients) { encodeVlessClient(it) }
            put("decryption", p.decryption)
            putJsonArray("fallbacks") { p.fallbacks.forEach { add(encodeFallback(it)) } }
        }.encode()

        is ProtocolSettings.Vmess -> buildJsonObject {
            putClients(p.clients) { encodeVmessClient(it) }
            put("disableInsecureEncryption", p.disableInsecureEncryption)
        }.encode()

        is ProtocolSettings.Trojan -> buildJsonObject {
            putClients(p.clients) { encodeTrojanClient(it) }
            putJsonArray("fallbacks") { p.fallbacks.forEach { add(encodeFallback(it)) } }
        }.encode()

        is ProtocolSettings.Shadowsocks -> buildJsonObject {
            put("method", p.method)
            put("password", p.password)
            put("network", p.network)
            putClients(p.clients) { encodeShadowsocksClient(it) }
        }.encode()

        is ProtocolSettings.Hysteria2 -> buildJsonObject {
            if (p.obfs != null) {
                putJsonObject("obfs") {
                    put("type", p.obfs.type)
                    put("password", p.obfs.password)
                }
            }
            put("ignore_client_bandwidth", p.ignoreClientBandwidth)
            putClients(p.clients) { encodeHy2Client(it) }
        }.encode()

        is ProtocolSettings.Socks -> buildJsonObject {
            put("auth", p.auth)
            putJsonArray("accounts") {
                p.accounts.forEach { acc ->
                    addJsonObject {
                        put("user", acc.user)
                        put("pass", acc.pass)
                    }
                }
            }
            put("udp", p.udp)
            put("ip", p.ip)
        }.encode()

        is ProtocolSettings.Http -> buildJsonObject {
            putJsonArray("accounts") {
                p.accounts.forEach { acc ->
                    addJsonObject {
                        put("user", acc.user)
                        put("pass", acc.pass)
                    }
                }
            }
            put("allowTransparent", p.allowTransparent)
        }.encode()

        is ProtocolSettings.Wireguard -> buildJsonObject {
            put("secretKey", p.secretKey)
            put("mtu", p.mtu)
            put("noKernelTun", p.noKernelTun)
            putJsonArray("peers") {
                p.peers.forEach { peer ->
                    addJsonObject {
                        put("publicKey", peer.publicKey)
                        put("presharedKey", peer.presharedKey)
                        put("keepAlive", peer.keepAlive)
                        putJsonArray("allowedIPs") { peer.allowedIPs.forEach { add(it) } }
                    }
                }
            }
        }.encode()

        is ProtocolSettings.Dokodemo -> buildJsonObject {
            put("address", p.address)
            put("port", p.targetPort)
            put("network", p.network)
            put("followRedirect", p.followRedirect)
        }.encode()
    }

    private inline fun <T> kotlinx.serialization.json.JsonObjectBuilder.putClients(
        clients: List<T>,
        crossinline encode: (T) -> JsonObject,
    ) {
        putJsonArray("clients") { clients.forEach { add(encode(it)) } }
    }

    private fun encodeVlessClient(c: VlessClient): JsonObject = buildJsonObject {
        put("id", c.id)
        put("flow", c.flow)
        putCommonClientFields(
            email = c.email, totalGB = c.totalGB, expiryTime = c.expiryTime,
            limitIp = c.limitIp, subId = c.subId, tgId = c.tgId, comment = c.comment,
            reset = c.reset, enable = c.enable,
        )
    }

    private fun encodeVmessClient(c: VmessClient): JsonObject = buildJsonObject {
        put("id", c.id)
        putCommonClientFields(
            email = c.email, totalGB = c.totalGB, expiryTime = c.expiryTime,
            limitIp = c.limitIp, subId = c.subId, tgId = c.tgId, comment = c.comment,
            reset = c.reset, enable = c.enable,
        )
    }

    private fun encodeTrojanClient(c: TrojanClient): JsonObject = buildJsonObject {
        put("password", c.password)
        put("flow", c.flow)
        putCommonClientFields(
            email = c.email, totalGB = c.totalGB, expiryTime = c.expiryTime,
            limitIp = c.limitIp, subId = c.subId, tgId = c.tgId, comment = c.comment,
            reset = c.reset, enable = c.enable,
        )
    }

    private fun encodeShadowsocksClient(c: ShadowsocksClient): JsonObject = buildJsonObject {
        put("password", c.password)
        put("method", c.method)
        putCommonClientFields(
            email = c.email, totalGB = c.totalGB, expiryTime = c.expiryTime,
            limitIp = c.limitIp, subId = c.subId, tgId = c.tgId, comment = c.comment,
            reset = c.reset, enable = c.enable,
        )
    }

    private fun encodeHy2Client(c: Hy2Client): JsonObject = buildJsonObject {
        put("auth", c.auth)
        putCommonClientFields(
            email = c.email, totalGB = c.totalGB, expiryTime = c.expiryTime,
            limitIp = c.limitIp, subId = c.subId, tgId = c.tgId, comment = c.comment,
            reset = c.reset, enable = c.enable,
        )
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putCommonClientFields(
        email: String, totalGB: Long, expiryTime: Long, limitIp: Int,
        subId: String, tgId: String, comment: String, reset: Int, enable: Boolean,
    ) {
        put("email", email)
        put("limitIp", limitIp)
        put("totalGB", totalGB)
        put("expiryTime", expiryTime)
        put("enable", enable)
        put("tgId", tgId)
        put("subId", subId)
        put("comment", comment)
        put("reset", reset)
    }

    private fun encodeFallback(f: Fallback): JsonObject = buildJsonObject {
        put("alpn", f.alpn)
        put("name", f.name)
        put("path", f.path)
        put("dest", f.dest)
        put("xver", f.xver)
    }

    // ---- streamSettings ----

    private fun encodeStreamSettings(stream: StreamConfig?): String {
        if (stream == null) return "{}"
        return buildJsonObject {
            put("network", stream.transport.network)
            put("security", stream.security.type)
            encodeTransport(stream.transport)
            encodeSecurity(stream.security)
        }.encode()
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.encodeTransport(t: TransportConfig) {
        when (t) {
            is TransportConfig.Tcp -> putJsonObject("tcpSettings") {
                putJsonObject("header") {
                    when (val h = t.header) {
                        is TcpHeader.None -> put("type", "none")
                        is TcpHeader.Http -> {
                            put("type", "http")
                            putJsonObject("request") {
                                put("version", "1.1")
                                put("method", "GET")
                                putJsonArray("path") { add(h.path) }
                                putJsonObject("headers") {
                                    if (h.host.isNotEmpty()) {
                                        putJsonArray("Host") { add(h.host) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is TransportConfig.Ws -> putJsonObject("wsSettings") {
                put("path", t.path)
                put("host", t.host)
                putJsonObject("headers") {
                    t.headers.forEach { (k, v) -> put(k, v) }
                }
            }

            is TransportConfig.Grpc -> putJsonObject("grpcSettings") {
                put("serviceName", t.serviceName)
                put("authority", t.authority)
                put("multiMode", t.multiMode)
            }

            is TransportConfig.HttpUpgrade -> putJsonObject("httpupgradeSettings") {
                put("path", t.path)
                put("host", t.host)
            }

            is TransportConfig.XHttp -> putJsonObject("xhttpSettings") {
                put("path", t.path)
                put("host", t.host)
                put("mode", t.mode)
            }

            is TransportConfig.Kcp -> putJsonObject("kcpSettings") {
                put("mtu", t.mtu)
                put("tti", t.tti)
                put("uplinkCapacity", t.uplinkCapacity)
                put("downlinkCapacity", t.downlinkCapacity)
                put("congestion", t.congestion)
                put("readBufferSize", t.readBufferSize)
                put("writeBufferSize", t.writeBufferSize)
                put("seed", t.seed)
                putJsonObject("header") { put("type", t.header.type) }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.encodeSecurity(s: SecurityConfig) {
        when (s) {
            is SecurityConfig.None -> Unit

            is SecurityConfig.Tls -> putJsonObject("tlsSettings") {
                put("serverName", s.serverName)
                put("minVersion", s.minVersion)
                put("maxVersion", s.maxVersion)
                putJsonArray("alpn") { s.alpn.forEach { add(it) } }
                putJsonArray("certificates") {
                    s.certificates.forEach { cert ->
                        addJsonObject {
                            put("certificateFile", cert.certificateFile)
                            put("keyFile", cert.keyFile)
                            putJsonArray("certificate") { cert.certificate.forEach { add(it) } }
                            putJsonArray("key") { cert.key.forEach { add(it) } }
                        }
                    }
                }
                putJsonObject("settings") {
                    put("fingerprint", s.fingerprint)
                }
            }

            is SecurityConfig.Reality -> putJsonObject("realitySettings") {
                put("show", s.show)
                put("xver", s.xver)
                put("dest", s.dest)
                putJsonArray("serverNames") { s.serverNames.forEach { add(it) } }
                put("privateKey", s.privateKey)
                putJsonArray("shortIds") { s.shortIds.forEach { add(it) } }
                put("minClient", s.minClient)
                put("maxClient", s.maxClient)
                put("maxTimediff", s.maxTimediff)
                putJsonObject("settings") {
                    put("publicKey", s.publicKey)
                    put("fingerprint", s.fingerprint)
                }
            }
        }
    }

    // ---- sniffing ----

    private fun encodeSniffing(s: SniffingConfig): String = buildJsonObject {
        put("enabled", s.enabled)
        putJsonArray("destOverride") { s.destOverride.forEach { add(it) } }
        put("metadataOnly", s.metadataOnly)
        put("routeOnly", s.routeOnly)
    }.encode()

    private fun JsonObject.encode(): String = json.encodeToString(JsonObject.serializer(), this)
}
