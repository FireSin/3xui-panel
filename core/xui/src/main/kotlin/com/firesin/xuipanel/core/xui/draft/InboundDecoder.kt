package com.firesin.xuipanel.core.xui.draft

import com.firesin.xuipanel.core.xui.dto.InboundDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes a server-returned [InboundDto] into a typed [InboundDraft].
 *
 * This is the inverse of [InboundEncoder]: the three JSON-stringified blobs
 * (settings / streamSettings / sniffing) are parsed back into the domain model so
 * that the Add/Edit form can be pre-populated for editing.
 *
 * Field names and protocol identifiers mirror [InboundEncoder] exactly.
 */
object InboundDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    /** Protocols that carry a meaningful streamSettings block. */
    private val STREAM_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria")

    fun decode(dto: InboundDto): InboundDraft {
        val settingsObj = runCatching { json.parseToJsonElement(dto.settings).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
        val streamObj = runCatching { json.parseToJsonElement(dto.streamSettings).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
        val sniffingObj = runCatching { json.parseToJsonElement(dto.sniffing).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))

        val stream = if (dto.protocol in STREAM_PROTOCOLS) decodeStream(streamObj) else null
        val sniffing = decodeSniffing(sniffingObj)
        val protocol = decodeProtocol(dto.protocol, settingsObj)

        return InboundDraft(
            remark = dto.remark,
            port = dto.port,
            listen = dto.listen,
            enable = dto.enable,
            expiryTime = dto.expiryTime,
            total = dto.total,
            protocol = protocol,
            stream = stream,
            sniffing = sniffing,
            nodeId = dto.nodeId,
        )
    }

    // ---- protocol ----

    private fun decodeProtocol(protocol: String, s: JsonObject): ProtocolSettings = when (protocol) {
        "vless" -> ProtocolSettings.Vless(
            clients = s["clients"]?.jsonArray?.map { decodeVlessClient(it.jsonObject) } ?: emptyList(),
            decryption = s["decryption"]?.jsonPrimitive?.contentOrNull ?: "none",
            fallbacks = s["fallbacks"]?.jsonArray?.map { decodeFallback(it.jsonObject) } ?: emptyList(),
        )
        "vmess" -> ProtocolSettings.Vmess(
            clients = s["clients"]?.jsonArray?.map { decodeVmessClient(it.jsonObject) } ?: emptyList(),
            disableInsecureEncryption = s["disableInsecureEncryption"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        "trojan" -> ProtocolSettings.Trojan(
            clients = s["clients"]?.jsonArray?.map { decodeTrojanClient(it.jsonObject) } ?: emptyList(),
            fallbacks = s["fallbacks"]?.jsonArray?.map { decodeFallback(it.jsonObject) } ?: emptyList(),
        )
        "shadowsocks" -> ProtocolSettings.Shadowsocks(
            method = s["method"]?.jsonPrimitive?.contentOrNull ?: "",
            password = s["password"]?.jsonPrimitive?.contentOrNull ?: "",
            network = s["network"]?.jsonPrimitive?.contentOrNull ?: "tcp,udp",
            clients = s["clients"]?.jsonArray?.map { decodeShadowsocksClient(it.jsonObject) } ?: emptyList(),
        )
        "mixed" -> ProtocolSettings.Socks(
            auth = s["auth"]?.jsonPrimitive?.contentOrNull ?: "noauth",
            accounts = s["accounts"]?.jsonArray?.map {
                val o = it.jsonObject
                UserPass(
                    user = o["user"]?.jsonPrimitive?.contentOrNull ?: "",
                    pass = o["pass"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList(),
            udp = s["udp"]?.jsonPrimitive?.booleanOrNull ?: false,
            ip = s["ip"]?.jsonPrimitive?.contentOrNull ?: "127.0.0.1",
        )
        "http" -> ProtocolSettings.Http(
            accounts = s["accounts"]?.jsonArray?.map {
                val o = it.jsonObject
                UserPass(
                    user = o["user"]?.jsonPrimitive?.contentOrNull ?: "",
                    pass = o["pass"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList(),
            allowTransparent = s["allowTransparent"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        "wireguard" -> ProtocolSettings.Wireguard(
            secretKey = s["secretKey"]?.jsonPrimitive?.contentOrNull ?: "",
            mtu = s["mtu"]?.jsonPrimitive?.intOrNull ?: 1420,
            noKernelTun = s["noKernelTun"]?.jsonPrimitive?.booleanOrNull ?: false,
            peers = s["peers"]?.jsonArray?.map { decodeWgPeer(it.jsonObject) } ?: emptyList(),
        )
        "tunnel" -> ProtocolSettings.Dokodemo(
            address = s["address"]?.jsonPrimitive?.contentOrNull ?: "",
            targetPort = s["port"]?.jsonPrimitive?.intOrNull ?: 80,
            network = s["network"]?.jsonPrimitive?.contentOrNull ?: "tcp,udp",
            followRedirect = s["followRedirect"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        "hysteria" -> ProtocolSettings.Hysteria(
            version = s["version"]?.jsonPrimitive?.intOrNull ?: 2,
            clients = s["clients"]?.jsonArray?.map { decodeHysteriaClient(it.jsonObject) } ?: emptyList(),
        )
        "tun" -> ProtocolSettings.Tun(
            mtu = s["mtu"]?.jsonPrimitive?.intOrNull ?: 1500,
            gso = s["gso"]?.jsonPrimitive?.booleanOrNull ?: false,
            gro = s["gro"]?.jsonPrimitive?.booleanOrNull ?: false,
            enableExFilter = s["enableExFilter"]?.jsonPrimitive?.booleanOrNull ?: false,
            strictRoute = s["strictRoute"]?.jsonPrimitive?.booleanOrNull ?: true,
            routeAddress = s["routeAddress"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            routeAddressSet = s["routeAddressSet"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            routeExcludeAddress = s["routeExcludeAddress"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            routeExcludeAddressSet = s["routeExcludeAddressSet"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
        else -> ProtocolSettings.Vless(clients = emptyList())
    }

    // ---- clients ----

    private fun decodeVlessClient(o: JsonObject): VlessClient = VlessClient(
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
        email = o["email"]?.jsonPrimitive?.contentOrNull ?: "",
        flow = o["flow"]?.jsonPrimitive?.contentOrNull ?: "",
        totalGB = o["totalGB"]?.jsonPrimitive?.longOrNull ?: 0L,
        expiryTime = o["expiryTime"]?.jsonPrimitive?.longOrNull ?: 0L,
        limitIp = o["limitIp"]?.jsonPrimitive?.intOrNull ?: 0,
        subId = o["subId"]?.jsonPrimitive?.contentOrNull ?: "",
        tgId = o["tgId"]?.jsonPrimitive?.contentOrNull ?: "",
        comment = o["comment"]?.jsonPrimitive?.contentOrNull ?: "",
        reset = o["reset"]?.jsonPrimitive?.intOrNull ?: 0,
        enable = o["enable"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun decodeVmessClient(o: JsonObject): VmessClient = VmessClient(
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
        email = o["email"]?.jsonPrimitive?.contentOrNull ?: "",
        totalGB = o["totalGB"]?.jsonPrimitive?.longOrNull ?: 0L,
        expiryTime = o["expiryTime"]?.jsonPrimitive?.longOrNull ?: 0L,
        limitIp = o["limitIp"]?.jsonPrimitive?.intOrNull ?: 0,
        subId = o["subId"]?.jsonPrimitive?.contentOrNull ?: "",
        tgId = o["tgId"]?.jsonPrimitive?.contentOrNull ?: "",
        comment = o["comment"]?.jsonPrimitive?.contentOrNull ?: "",
        reset = o["reset"]?.jsonPrimitive?.intOrNull ?: 0,
        enable = o["enable"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun decodeTrojanClient(o: JsonObject): TrojanClient = TrojanClient(
        password = o["password"]?.jsonPrimitive?.contentOrNull ?: "",
        email = o["email"]?.jsonPrimitive?.contentOrNull ?: "",
        flow = o["flow"]?.jsonPrimitive?.contentOrNull ?: "",
        totalGB = o["totalGB"]?.jsonPrimitive?.longOrNull ?: 0L,
        expiryTime = o["expiryTime"]?.jsonPrimitive?.longOrNull ?: 0L,
        limitIp = o["limitIp"]?.jsonPrimitive?.intOrNull ?: 0,
        subId = o["subId"]?.jsonPrimitive?.contentOrNull ?: "",
        tgId = o["tgId"]?.jsonPrimitive?.contentOrNull ?: "",
        comment = o["comment"]?.jsonPrimitive?.contentOrNull ?: "",
        reset = o["reset"]?.jsonPrimitive?.intOrNull ?: 0,
        enable = o["enable"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun decodeShadowsocksClient(o: JsonObject): ShadowsocksClient = ShadowsocksClient(
        password = o["password"]?.jsonPrimitive?.contentOrNull ?: "",
        method = o["method"]?.jsonPrimitive?.contentOrNull ?: "",
        email = o["email"]?.jsonPrimitive?.contentOrNull ?: "",
        totalGB = o["totalGB"]?.jsonPrimitive?.longOrNull ?: 0L,
        expiryTime = o["expiryTime"]?.jsonPrimitive?.longOrNull ?: 0L,
        limitIp = o["limitIp"]?.jsonPrimitive?.intOrNull ?: 0,
        subId = o["subId"]?.jsonPrimitive?.contentOrNull ?: "",
        tgId = o["tgId"]?.jsonPrimitive?.contentOrNull ?: "",
        comment = o["comment"]?.jsonPrimitive?.contentOrNull ?: "",
        reset = o["reset"]?.jsonPrimitive?.intOrNull ?: 0,
        enable = o["enable"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun decodeHysteriaClient(o: JsonObject): HysteriaClient = HysteriaClient(
        auth = o["auth"]?.jsonPrimitive?.contentOrNull ?: "",
        email = o["email"]?.jsonPrimitive?.contentOrNull ?: "",
        totalGB = o["totalGB"]?.jsonPrimitive?.longOrNull ?: 0L,
        expiryTime = o["expiryTime"]?.jsonPrimitive?.longOrNull ?: 0L,
        limitIp = o["limitIp"]?.jsonPrimitive?.intOrNull ?: 0,
        subId = o["subId"]?.jsonPrimitive?.contentOrNull ?: "",
        tgId = o["tgId"]?.jsonPrimitive?.contentOrNull ?: "",
        comment = o["comment"]?.jsonPrimitive?.contentOrNull ?: "",
        reset = o["reset"]?.jsonPrimitive?.intOrNull ?: 0,
        enable = o["enable"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun decodeWgPeer(o: JsonObject): WgPeer = WgPeer(
        publicKey = o["publicKey"]?.jsonPrimitive?.contentOrNull ?: "",
        allowedIPs = o["allowedIPs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("0.0.0.0/0", "::/0"),
        presharedKey = o["presharedKey"]?.jsonPrimitive?.contentOrNull ?: "",
        keepAlive = o["keepAlive"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    private fun decodeFallback(o: JsonObject): Fallback = Fallback(
        alpn = o["alpn"]?.jsonPrimitive?.contentOrNull ?: "",
        name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
        path = o["path"]?.jsonPrimitive?.contentOrNull ?: "",
        dest = o["dest"]?.jsonPrimitive?.contentOrNull ?: "",
        xver = o["xver"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    // ---- stream ----

    private fun decodeStream(s: JsonObject): StreamConfig {
        val network = s["network"]?.jsonPrimitive?.contentOrNull ?: "tcp"
        val security = s["security"]?.jsonPrimitive?.contentOrNull ?: "none"
        val transport = decodeTransport(network, s)
        val sec = decodeSecurity(security, s)
        return StreamConfig(transport = transport, security = sec)
    }

    private fun decodeTransport(network: String, s: JsonObject): TransportConfig = when (network) {
        "ws" -> {
            val ws = s["wsSettings"]?.jsonObject ?: JsonObject(emptyMap())
            TransportConfig.Ws(
                path = ws["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                host = ws["host"]?.jsonPrimitive?.contentOrNull ?: "",
                headers = ws["headers"]?.jsonObject?.entries?.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: "")
                } ?: emptyMap(),
            )
        }
        "grpc" -> {
            val grpc = s["grpcSettings"]?.jsonObject ?: JsonObject(emptyMap())
            TransportConfig.Grpc(
                serviceName = grpc["serviceName"]?.jsonPrimitive?.contentOrNull ?: "",
                authority = grpc["authority"]?.jsonPrimitive?.contentOrNull ?: "",
                multiMode = grpc["multiMode"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        "httpupgrade" -> {
            val hu = s["httpupgradeSettings"]?.jsonObject ?: JsonObject(emptyMap())
            TransportConfig.HttpUpgrade(
                path = hu["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                host = hu["host"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        "xhttp" -> {
            val xh = s["xhttpSettings"]?.jsonObject ?: JsonObject(emptyMap())
            TransportConfig.XHttp(
                path = xh["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                host = xh["host"]?.jsonPrimitive?.contentOrNull ?: "",
                mode = xh["mode"]?.jsonPrimitive?.contentOrNull ?: "auto",
            )
        }
        "kcp" -> {
            val kcp = s["kcpSettings"]?.jsonObject ?: JsonObject(emptyMap())
            val headerType = kcp["header"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull ?: "none"
            TransportConfig.Kcp(
                mtu = kcp["mtu"]?.jsonPrimitive?.intOrNull ?: 1350,
                tti = kcp["tti"]?.jsonPrimitive?.intOrNull ?: 50,
                uplinkCapacity = kcp["uplinkCapacity"]?.jsonPrimitive?.intOrNull ?: 5,
                downlinkCapacity = kcp["downlinkCapacity"]?.jsonPrimitive?.intOrNull ?: 20,
                congestion = kcp["congestion"]?.jsonPrimitive?.booleanOrNull ?: false,
                readBufferSize = kcp["readBufferSize"]?.jsonPrimitive?.intOrNull ?: 2,
                writeBufferSize = kcp["writeBufferSize"]?.jsonPrimitive?.intOrNull ?: 2,
                seed = kcp["seed"]?.jsonPrimitive?.contentOrNull ?: "",
                header = decodeKcpHeader(headerType),
            )
        }
        "hysteria" -> {
            val hy = s["hysteriaSettings"]?.jsonObject ?: JsonObject(emptyMap())
            TransportConfig.HysteriaTransport(
                auth = hy["auth"]?.jsonPrimitive?.contentOrNull ?: "",
                udpIdleTimeout = hy["udpIdleTimeout"]?.jsonPrimitive?.intOrNull ?: 60,
            )
        }
        else -> { // "tcp" or unknown
            val tcp = s["tcpSettings"]?.jsonObject ?: JsonObject(emptyMap())
            val header = tcp["header"]?.jsonObject ?: JsonObject(emptyMap())
            val headerType = header["type"]?.jsonPrimitive?.contentOrNull ?: "none"
            TransportConfig.Tcp(
                header = if (headerType == "http") {
                    val request = header["request"]?.jsonObject ?: JsonObject(emptyMap())
                    val path = request["path"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: "/"
                    val host = request["headers"]?.jsonObject
                        ?.get("Host")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: ""
                    TcpHeader.Http(path = path, host = host)
                } else {
                    TcpHeader.None
                },
            )
        }
    }

    private fun decodeKcpHeader(type: String): KcpHeader = when (type) {
        "srtp" -> KcpHeader.Srtp
        "utp" -> KcpHeader.Utp
        "wechat-video" -> KcpHeader.WechatVideo
        "dtls" -> KcpHeader.DtlsHeader
        "wireguard" -> KcpHeader.WireguardHeader
        else -> KcpHeader.None
    }

    private fun decodeSecurity(security: String, s: JsonObject): SecurityConfig = when (security) {
        "tls" -> {
            val tls = s["tlsSettings"]?.jsonObject ?: JsonObject(emptyMap())
            val settings = tls["settings"]?.jsonObject ?: JsonObject(emptyMap())
            SecurityConfig.Tls(
                serverName = tls["serverName"]?.jsonPrimitive?.contentOrNull ?: "",
                minVersion = tls["minVersion"]?.jsonPrimitive?.contentOrNull ?: "1.2",
                maxVersion = tls["maxVersion"]?.jsonPrimitive?.contentOrNull ?: "1.3",
                alpn = tls["alpn"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                certificates = tls["certificates"]?.jsonArray?.map { cert ->
                    val o = cert.jsonObject
                    TlsCertificate(
                        certificateFile = o["certificateFile"]?.jsonPrimitive?.contentOrNull ?: "",
                        keyFile = o["keyFile"]?.jsonPrimitive?.contentOrNull ?: "",
                        certificate = o["certificate"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        key = o["key"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    )
                } ?: emptyList(),
                fingerprint = settings["fingerprint"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        "reality" -> {
            val r = s["realitySettings"]?.jsonObject ?: JsonObject(emptyMap())
            val settings = r["settings"]?.jsonObject ?: JsonObject(emptyMap())
            SecurityConfig.Reality(
                show = r["show"]?.jsonPrimitive?.booleanOrNull ?: false,
                xver = r["xver"]?.jsonPrimitive?.intOrNull ?: 0,
                dest = r["dest"]?.jsonPrimitive?.contentOrNull ?: "",
                serverNames = r["serverNames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                privateKey = r["privateKey"]?.jsonPrimitive?.contentOrNull ?: "",
                publicKey = settings["publicKey"]?.jsonPrimitive?.contentOrNull ?: "",
                shortIds = r["shortIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                minClient = r["minClient"]?.jsonPrimitive?.contentOrNull ?: "",
                maxClient = r["maxClient"]?.jsonPrimitive?.contentOrNull ?: "",
                maxTimediff = r["maxTimediff"]?.jsonPrimitive?.intOrNull ?: 0,
                fingerprint = settings["fingerprint"]?.jsonPrimitive?.contentOrNull ?: "chrome",
            )
        }
        else -> SecurityConfig.None
    }

    // ---- sniffing ----

    private fun decodeSniffing(s: JsonObject): SniffingConfig = SniffingConfig(
        enabled = s["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
        destOverride = s["destOverride"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: listOf("http", "tls", "quic"),
        metadataOnly = s["metadataOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
        routeOnly = s["routeOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
    )
}
