package com.firesin.xuipanel.feature.inbounds.add.ui

import com.firesin.xuipanel.core.xui.draft.KcpHeader
import com.firesin.xuipanel.core.xui.draft.SecurityConfig
import com.firesin.xuipanel.core.xui.draft.SniffingConfig
import com.firesin.xuipanel.core.xui.draft.StreamConfig
import com.firesin.xuipanel.core.xui.draft.TcpHeader
import com.firesin.xuipanel.core.xui.draft.TransportConfig
import com.firesin.xuipanel.core.xui.draft.Fallback
import com.firesin.xuipanel.core.xui.draft.Hy2Client
import com.firesin.xuipanel.core.xui.draft.Hy2Obfs
import com.firesin.xuipanel.core.xui.draft.InboundDraft
import com.firesin.xuipanel.core.xui.draft.ProtocolSettings
import com.firesin.xuipanel.core.xui.draft.ShadowsocksClient
import com.firesin.xuipanel.core.xui.draft.TlsCertificate
import com.firesin.xuipanel.core.xui.draft.TrojanClient
import com.firesin.xuipanel.core.xui.draft.UserPass
import com.firesin.xuipanel.core.xui.draft.VlessClient
import com.firesin.xuipanel.core.xui.draft.VmessClient
import com.firesin.xuipanel.core.xui.draft.WgPeer
import com.firesin.xuipanel.core.xui.util.randomUuid

enum class ProtocolType(val label: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    WIREGUARD("WireGuard"),
    SOCKS("Mixed"),
    HTTP("HTTP"),
    DOKODEMO("Tunnel"),
    // HYSTERIA2 deferred — needs network=hysteria transport, mandatory TLS settings,
    // hysteriaSettings stream block, and {version:2, clients:[{auth,...}]} settings shape.
    // Re-add when the dedicated transport form is built.
}

enum class NetworkType(val label: String) {
    TCP("TCP"),
    WS("WebSocket"),
    GRPC("gRPC"),
    HTTPUPGRADE("HttpUpgrade"),
    XHTTP("XHTTP"),
    KCP("mKCP"),
}

enum class SecurityType(val label: String) {
    NONE("None"),
    TLS("TLS"),
    REALITY("Reality"),
}

data class VlessClientState(
    val id: String = randomUuid(),
    val email: String = "",
    val flow: String = "",
    val totalGb: String = "0",
    val expiryTime: Long = 0L,
    val enable: Boolean = true,
    val subId: String = "",
)

data class VmessClientState(
    val id: String = randomUuid(),
    val email: String = "",
    val totalGb: String = "0",
    val expiryTime: Long = 0L,
    val enable: Boolean = true,
    val subId: String = "",
)

data class TrojanClientState(
    val password: String = randomUuid(),
    val email: String = "",
    val flow: String = "",
    val totalGb: String = "0",
    val expiryTime: Long = 0L,
    val enable: Boolean = true,
    val subId: String = "",
)

data class ShadowsocksClientState(
    val password: String = "",
    val email: String = "",
    val totalGb: String = "0",
    val expiryTime: Long = 0L,
    val enable: Boolean = true,
    val subId: String = "",
)

data class Hy2ClientState(
    /** Sent over the wire as `auth` (hysteria2 client identifier). */
    val auth: String = randomShortId() + randomShortId(),
    val email: String = "",
    val totalGb: String = "0",
    val expiryTime: Long = 0L,
    val enable: Boolean = true,
    val subId: String = "",
)

data class UserPassState(val user: String = "", val pass: String = "")

data class WgPeerState(
    val publicKey: String = "",
    val allowedIPs: String = "0.0.0.0/0, ::/0",
    val presharedKey: String = "",
    val keepAlive: String = "0",
)

data class AddInboundFormState(
    // Common
    val remark: String = "",
    val port: String = "443",
    val listen: String = "",
    val enable: Boolean = true,
    val expiryTime: Long = 0L,
    val totalGb: String = "0",

    // Protocol selector
    val selectedProtocol: ProtocolType = ProtocolType.VLESS,

    // VLESS
    val vlessClients: List<VlessClientState> = listOf(VlessClientState()),
    val vlessDecryption: String = "none",
    val vlessFallbacks: List<Fallback> = emptyList(),

    // VMess
    val vmessClients: List<VmessClientState> = listOf(VmessClientState()),
    val vmessDisableInsecure: Boolean = false,

    // Trojan
    val trojanClients: List<TrojanClientState> = listOf(TrojanClientState()),
    val trojanFallbacks: List<Fallback> = emptyList(),

    // Shadowsocks
    val ssMethod: String = "chacha20-ietf-poly1305",
    val ssPassword: String = "",
    val ssNetwork: String = "tcp,udp",
    val ssClients: List<ShadowsocksClientState> = emptyList(),

    // Hysteria2
    val hy2ObfsEnabled: Boolean = false,
    val hy2ObfsPassword: String = "",
    val hy2IgnoreClientBandwidth: Boolean = false,
    val hy2Clients: List<Hy2ClientState> = listOf(Hy2ClientState()),

    // SOCKS
    val socksAuth: String = "noauth",
    val socksAccounts: List<UserPassState> = emptyList(),
    val socksUdp: Boolean = false,
    val socksIp: String = "127.0.0.1",

    // HTTP
    val httpAccounts: List<UserPassState> = emptyList(),
    val httpAllowTransparent: Boolean = false,

    // WireGuard
    val wgSecretKey: String = "",
    val wgMtu: String = "1420",
    val wgPeers: List<WgPeerState> = listOf(WgPeerState()),
    val wgNoKernelTun: Boolean = false,

    // Dokodemo
    val dokodemoAddress: String = "",
    val dokodemoTargetPort: String = "80",
    val dokodemoNetwork: String = "tcp",
    val dokodemoFollowRedirect: Boolean = false,

    // Stream / Transport
    val selectedNetwork: NetworkType = NetworkType.TCP,

    // TCP
    val tcpHeaderType: String = "none",
    val tcpHttpPath: String = "/",
    val tcpHttpHost: String = "",

    // WS
    val wsPath: String = "/",
    val wsHost: String = "",

    // gRPC
    val grpcServiceName: String = "",
    val grpcAuthority: String = "",
    val grpcMultiMode: Boolean = false,

    // HttpUpgrade
    val httpUpgradePath: String = "/",
    val httpUpgradeHost: String = "",

    // XHTTP
    val xhttpPath: String = "/",
    val xhttpHost: String = "",
    val xhttpMode: String = "auto",

    // KCP
    val kcpMtu: String = "1350",
    val kcpTti: String = "50",
    val kcpUplinkCapacity: String = "5",
    val kcpDownlinkCapacity: String = "20",
    val kcpCongestion: Boolean = false,
    val kcpReadBufferSize: String = "2",
    val kcpWriteBufferSize: String = "2",
    val kcpSeed: String = "",
    val kcpHeaderType: String = "none",

    // Security
    val selectedSecurity: SecurityType = SecurityType.REALITY,

    // TLS
    val tlsServerName: String = "",
    val tlsMinVersion: String = "1.2",
    val tlsMaxVersion: String = "1.3",
    val tlsAlpn: Set<String> = emptySet(),
    val tlsCertificateFile: String = "",
    val tlsKeyFile: String = "",
    val tlsFingerprint: String = "",

    // Reality
    val realityDest: String = "",
    val realityServerNames: String = "",
    val realityPrivateKey: String = "",
    val realityPublicKey: String = "",
    val realityShortIds: String = randomShortId(),
    val realityFingerprint: String = "chrome",

    // Sniffing
    val sniffingEnabled: Boolean = true,
    val sniffingDestOverride: Set<String> = setOf("http", "tls", "quic"),
    val sniffingMetadataOnly: Boolean = false,
    val sniffingRouteOnly: Boolean = false,
) {
    companion object
}

private const val SHORT_ID_LEN = 8
fun randomShortId(): String {
    val hex = "0123456789abcdef"
    return buildString(SHORT_ID_LEN) { repeat(SHORT_ID_LEN) { append(hex.random()) } }
}

fun AddInboundFormState.toInboundDraft(): InboundDraft {
    val portInt = port.toIntOrNull() ?: 443
    val totalBytes = (totalGb.toLongOrNull() ?: 0L) * BYTES_PER_GB

    val protocol = buildProtocolSettings(this)
    val stream = buildStreamConfig(this)
    val sniffing = SniffingConfig(
        enabled = sniffingEnabled,
        destOverride = sniffingDestOverride.toList(),
        metadataOnly = sniffingMetadataOnly,
        routeOnly = sniffingRouteOnly,
    )

    return InboundDraft(
        remark = remark,
        port = portInt,
        listen = listen,
        enable = enable,
        expiryTime = expiryTime,
        total = totalBytes,
        protocol = protocol,
        stream = stream,
        sniffing = sniffing,
    )
}

private const val BYTES_PER_GB = 1_073_741_824L

private fun buildProtocolSettings(s: AddInboundFormState): ProtocolSettings = when (s.selectedProtocol) {
    ProtocolType.VLESS -> ProtocolSettings.Vless(
        clients = s.vlessClients.map { c ->
            VlessClient(
                id = c.id,
                email = c.email,
                flow = c.flow,
                totalGB = (c.totalGb.toLongOrNull() ?: 0L) * BYTES_PER_GB,
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        },
        decryption = s.vlessDecryption,
        fallbacks = s.vlessFallbacks,
    )
    ProtocolType.VMESS -> ProtocolSettings.Vmess(
        clients = s.vmessClients.map { c ->
            VmessClient(
                id = c.id,
                email = c.email,
                totalGB = (c.totalGb.toLongOrNull() ?: 0L) * BYTES_PER_GB,
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        },
        disableInsecureEncryption = s.vmessDisableInsecure,
    )
    ProtocolType.TROJAN -> ProtocolSettings.Trojan(
        clients = s.trojanClients.map { c ->
            TrojanClient(
                password = c.password,
                email = c.email,
                flow = c.flow,
                totalGB = (c.totalGb.toLongOrNull() ?: 0L) * BYTES_PER_GB,
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        },
        fallbacks = s.trojanFallbacks,
    )
    ProtocolType.SHADOWSOCKS -> ProtocolSettings.Shadowsocks(
        method = s.ssMethod,
        password = s.ssPassword,
        network = s.ssNetwork,
        clients = s.ssClients.map { c ->
            ShadowsocksClient(
                password = c.password,
                email = c.email,
                totalGB = (c.totalGb.toLongOrNull() ?: 0L) * BYTES_PER_GB,
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        },
    )
    ProtocolType.SOCKS -> ProtocolSettings.Socks(
        auth = s.socksAuth,
        accounts = s.socksAccounts.map { UserPass(it.user, it.pass) },
        udp = s.socksUdp,
        ip = s.socksIp,
    )
    ProtocolType.HTTP -> ProtocolSettings.Http(
        accounts = s.httpAccounts.map { UserPass(it.user, it.pass) },
        allowTransparent = s.httpAllowTransparent,
    )
    ProtocolType.WIREGUARD -> ProtocolSettings.Wireguard(
        secretKey = s.wgSecretKey,
        mtu = s.wgMtu.toIntOrNull() ?: 1420,
        peers = s.wgPeers.map { p ->
            WgPeer(
                publicKey = p.publicKey,
                allowedIPs = p.allowedIPs.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                presharedKey = p.presharedKey,
                keepAlive = p.keepAlive.toIntOrNull() ?: 0,
            )
        },
        noKernelTun = s.wgNoKernelTun,
    )
    ProtocolType.DOKODEMO -> ProtocolSettings.Dokodemo(
        address = s.dokodemoAddress,
        targetPort = s.dokodemoTargetPort.toIntOrNull() ?: 80,
        network = s.dokodemoNetwork,
        followRedirect = s.dokodemoFollowRedirect,
    )
}

fun AddInboundFormState.Companion.fromInboundDraft(draft: InboundDraft): AddInboundFormState {
    val protocolType = when (draft.protocol.protocolName) {
        "vless" -> ProtocolType.VLESS
        "vmess" -> ProtocolType.VMESS
        "trojan" -> ProtocolType.TROJAN
        "shadowsocks" -> ProtocolType.SHADOWSOCKS
        "wireguard" -> ProtocolType.WIREGUARD
        "mixed" -> ProtocolType.SOCKS
        "http" -> ProtocolType.HTTP
        "tunnel" -> ProtocolType.DOKODEMO
        else -> ProtocolType.VLESS // hysteria2 — edit unsupported, fallback
    }

    val base = AddInboundFormState(
        remark = draft.remark,
        port = draft.port.toString(),
        listen = draft.listen,
        enable = draft.enable,
        expiryTime = draft.expiryTime,
        totalGb = if (draft.total <= 0L) "0" else (draft.total / BYTES_PER_GB).toString(),
        selectedProtocol = protocolType,
        // sniffing
        sniffingEnabled = draft.sniffing.enabled,
        sniffingDestOverride = draft.sniffing.destOverride.toSet(),
        sniffingMetadataOnly = draft.sniffing.metadataOnly,
        sniffingRouteOnly = draft.sniffing.routeOnly,
    )

    val withProtocol = applyProtocol(base, draft.protocol)
    return applyStream(withProtocol, draft.stream)
}

private fun applyProtocol(s: AddInboundFormState, p: com.firesin.xuipanel.core.xui.draft.ProtocolSettings): AddInboundFormState = when (p) {
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Vless -> s.copy(
        vlessClients = p.clients.map { c ->
            VlessClientState(
                id = c.id,
                email = c.email,
                flow = c.flow,
                totalGb = if (c.totalGB <= 0L) "0" else (c.totalGB / BYTES_PER_GB).toString(),
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        }.ifEmpty { listOf(VlessClientState()) },
        vlessDecryption = p.decryption,
        vlessFallbacks = p.fallbacks,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Vmess -> s.copy(
        vmessClients = p.clients.map { c ->
            VmessClientState(
                id = c.id,
                email = c.email,
                totalGb = if (c.totalGB <= 0L) "0" else (c.totalGB / BYTES_PER_GB).toString(),
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        }.ifEmpty { listOf(VmessClientState()) },
        vmessDisableInsecure = p.disableInsecureEncryption,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Trojan -> s.copy(
        trojanClients = p.clients.map { c ->
            TrojanClientState(
                password = c.password,
                email = c.email,
                flow = c.flow,
                totalGb = if (c.totalGB <= 0L) "0" else (c.totalGB / BYTES_PER_GB).toString(),
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        }.ifEmpty { listOf(TrojanClientState()) },
        trojanFallbacks = p.fallbacks,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Shadowsocks -> s.copy(
        ssMethod = p.method,
        ssPassword = p.password,
        ssNetwork = p.network,
        ssClients = p.clients.map { c ->
            ShadowsocksClientState(
                password = c.password,
                email = c.email,
                totalGb = if (c.totalGB <= 0L) "0" else (c.totalGB / BYTES_PER_GB).toString(),
                expiryTime = c.expiryTime,
                enable = c.enable,
                subId = c.subId,
            )
        },
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Socks -> s.copy(
        socksAuth = p.auth,
        socksAccounts = p.accounts.map { UserPassState(user = it.user, pass = it.pass) },
        socksUdp = p.udp,
        socksIp = p.ip,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Http -> s.copy(
        httpAccounts = p.accounts.map { UserPassState(user = it.user, pass = it.pass) },
        httpAllowTransparent = p.allowTransparent,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Wireguard -> s.copy(
        wgSecretKey = p.secretKey,
        wgMtu = p.mtu.toString(),
        wgNoKernelTun = p.noKernelTun,
        wgPeers = p.peers.map { peer ->
            WgPeerState(
                publicKey = peer.publicKey,
                allowedIPs = peer.allowedIPs.joinToString(", "),
                presharedKey = peer.presharedKey,
                keepAlive = peer.keepAlive.toString(),
            )
        }.ifEmpty { listOf(WgPeerState()) },
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Dokodemo -> s.copy(
        dokodemoAddress = p.address,
        dokodemoTargetPort = p.targetPort.toString(),
        dokodemoNetwork = p.network,
        dokodemoFollowRedirect = p.followRedirect,
    )
    is com.firesin.xuipanel.core.xui.draft.ProtocolSettings.Hysteria2 -> s // unsupported — no-op
}

private fun applyStream(s: AddInboundFormState, stream: com.firesin.xuipanel.core.xui.draft.StreamConfig?): AddInboundFormState {
    if (stream == null) return s

    val withTransport = when (val t = stream.transport) {
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.Tcp -> s.copy(
            selectedNetwork = NetworkType.TCP,
            tcpHeaderType = when (t.header) {
                is TcpHeader.Http -> "http"
                TcpHeader.None -> "none"
            },
            tcpHttpPath = (t.header as? TcpHeader.Http)?.path ?: "/",
            tcpHttpHost = (t.header as? TcpHeader.Http)?.host ?: "",
        )
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.Ws -> s.copy(
            selectedNetwork = NetworkType.WS,
            wsPath = t.path,
            wsHost = t.host,
        )
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.Grpc -> s.copy(
            selectedNetwork = NetworkType.GRPC,
            grpcServiceName = t.serviceName,
            grpcAuthority = t.authority,
            grpcMultiMode = t.multiMode,
        )
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.HttpUpgrade -> s.copy(
            selectedNetwork = NetworkType.HTTPUPGRADE,
            httpUpgradePath = t.path,
            httpUpgradeHost = t.host,
        )
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.XHttp -> s.copy(
            selectedNetwork = NetworkType.XHTTP,
            xhttpPath = t.path,
            xhttpHost = t.host,
            xhttpMode = t.mode,
        )
        is com.firesin.xuipanel.core.xui.draft.TransportConfig.Kcp -> s.copy(
            selectedNetwork = NetworkType.KCP,
            kcpMtu = t.mtu.toString(),
            kcpTti = t.tti.toString(),
            kcpUplinkCapacity = t.uplinkCapacity.toString(),
            kcpDownlinkCapacity = t.downlinkCapacity.toString(),
            kcpCongestion = t.congestion,
            kcpReadBufferSize = t.readBufferSize.toString(),
            kcpWriteBufferSize = t.writeBufferSize.toString(),
            kcpSeed = t.seed,
            kcpHeaderType = t.header.type,
        )
    }

    return when (val sec = stream.security) {
        com.firesin.xuipanel.core.xui.draft.SecurityConfig.None -> withTransport.copy(
            selectedSecurity = SecurityType.NONE,
        )
        is com.firesin.xuipanel.core.xui.draft.SecurityConfig.Tls -> withTransport.copy(
            selectedSecurity = SecurityType.TLS,
            tlsServerName = sec.serverName,
            tlsMinVersion = sec.minVersion,
            tlsMaxVersion = sec.maxVersion,
            tlsAlpn = sec.alpn.toSet(),
            tlsCertificateFile = sec.certificates.firstOrNull()?.certificateFile ?: "",
            tlsKeyFile = sec.certificates.firstOrNull()?.keyFile ?: "",
            tlsFingerprint = sec.fingerprint,
        )
        is com.firesin.xuipanel.core.xui.draft.SecurityConfig.Reality -> withTransport.copy(
            selectedSecurity = SecurityType.REALITY,
            realityDest = sec.dest,
            realityServerNames = sec.serverNames.joinToString("\n"),
            realityPrivateKey = sec.privateKey,
            realityPublicKey = sec.publicKey,
            realityShortIds = sec.shortIds.joinToString("\n"),
            realityFingerprint = sec.fingerprint,
        )
    }
}

private val STREAM_PROTOCOLS = setOf(
    ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN, ProtocolType.SHADOWSOCKS
)

private fun buildStreamConfig(s: AddInboundFormState): StreamConfig? {
    if (s.selectedProtocol !in STREAM_PROTOCOLS) return null

    val transport = when (s.selectedNetwork) {
        NetworkType.TCP -> TransportConfig.Tcp(
            header = if (s.tcpHeaderType == "http") {
                TcpHeader.Http(path = s.tcpHttpPath, host = s.tcpHttpHost)
            } else {
                TcpHeader.None
            },
        )
        NetworkType.WS -> TransportConfig.Ws(path = s.wsPath, host = s.wsHost)
        NetworkType.GRPC -> TransportConfig.Grpc(
            serviceName = s.grpcServiceName,
            authority = s.grpcAuthority,
            multiMode = s.grpcMultiMode,
        )
        NetworkType.HTTPUPGRADE -> TransportConfig.HttpUpgrade(
            path = s.httpUpgradePath,
            host = s.httpUpgradeHost,
        )
        NetworkType.XHTTP -> TransportConfig.XHttp(
            path = s.xhttpPath,
            host = s.xhttpHost,
            mode = s.xhttpMode,
        )
        NetworkType.KCP -> TransportConfig.Kcp(
            mtu = s.kcpMtu.toIntOrNull() ?: 1350,
            tti = s.kcpTti.toIntOrNull() ?: 50,
            uplinkCapacity = s.kcpUplinkCapacity.toIntOrNull() ?: 5,
            downlinkCapacity = s.kcpDownlinkCapacity.toIntOrNull() ?: 20,
            congestion = s.kcpCongestion,
            readBufferSize = s.kcpReadBufferSize.toIntOrNull() ?: 2,
            writeBufferSize = s.kcpWriteBufferSize.toIntOrNull() ?: 2,
            seed = s.kcpSeed,
            header = when (s.kcpHeaderType) {
                "srtp" -> KcpHeader.Srtp
                "utp" -> KcpHeader.Utp
                "wechat-video" -> KcpHeader.WechatVideo
                "dtls" -> KcpHeader.DtlsHeader
                "wireguard" -> KcpHeader.WireguardHeader
                else -> KcpHeader.None
            },
        )
    }

    val security = when (s.selectedSecurity) {
        SecurityType.NONE -> SecurityConfig.None
        SecurityType.TLS -> SecurityConfig.Tls(
            serverName = s.tlsServerName,
            minVersion = s.tlsMinVersion,
            maxVersion = s.tlsMaxVersion,
            alpn = s.tlsAlpn.toList(),
            certificates = if (s.tlsCertificateFile.isNotEmpty() || s.tlsKeyFile.isNotEmpty()) {
                listOf(TlsCertificate(certificateFile = s.tlsCertificateFile, keyFile = s.tlsKeyFile))
            } else {
                emptyList()
            },
            fingerprint = s.tlsFingerprint,
        )
        SecurityType.REALITY -> SecurityConfig.Reality(
            dest = s.realityDest,
            serverNames = s.realityServerNames.lines().map { it.trim() }.filter { it.isNotEmpty() },
            privateKey = s.realityPrivateKey,
            publicKey = s.realityPublicKey,
            shortIds = s.realityShortIds.lines().map { it.trim() }.filter { it.isNotEmpty() },
            fingerprint = s.realityFingerprint,
        )
    }

    return StreamConfig(transport = transport, security = security)
}
