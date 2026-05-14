package com.firesin.xuipanel.core.xui.draft

/**
 * Typed in-memory representation of an inbound being created or edited.
 *
 * 3x-ui stores three nested JSON blobs as plain strings in the `inbounds` table
 * (`settings`, `streamSettings`, `sniffing`). [InboundEncoder] turns this draft into
 * those strings + the top-level fields the API expects.
 *
 * The model intentionally mirrors the upstream Xray schemas (see web/service/inbound.go
 * and xray/inbound.go) rather than inventing our own — fewer surprises when round-tripping.
 *
 * Protocols, transports, and security layers are sealed: adding a new variant forces
 * the encoder + UI to be updated together.
 */
data class InboundDraft(
    val remark: String,
    val port: Int,
    val listen: String = "",
    val enable: Boolean = true,
    val expiryTime: Long = 0L,
    val total: Long = 0L,
    val protocol: ProtocolSettings,
    /** Stream settings — only meaningful for vmess/vless/trojan/shadowsocks. Null for socks/http/wg/dokodemo. */
    val stream: StreamConfig? = null,
    val sniffing: SniffingConfig = SniffingConfig.Default,
)

// ----- Protocol-specific settings (drives the `settings` JSON) -----

sealed interface ProtocolSettings {
    val protocolName: String

    data class Vless(
        val clients: List<VlessClient>,
        /** "none" — VLESS has no encryption; the field exists for forward-compat. */
        val decryption: String = "none",
        val fallbacks: List<Fallback> = emptyList(),
    ) : ProtocolSettings {
        override val protocolName: String = "vless"
    }

    data class Vmess(
        val clients: List<VmessClient>,
        val disableInsecureEncryption: Boolean = false,
    ) : ProtocolSettings {
        override val protocolName: String = "vmess"
    }

    data class Trojan(
        val clients: List<TrojanClient>,
        val fallbacks: List<Fallback> = emptyList(),
    ) : ProtocolSettings {
        override val protocolName: String = "trojan"
    }

    data class Shadowsocks(
        /** Inbound-level method, e.g. "chacha20-ietf-poly1305" or "2022-blake3-aes-128-gcm". */
        val method: String,
        /** Inbound-level password (required by SS-2022 ciphers; empty for legacy). */
        val password: String,
        /** "tcp", "udp", or "tcp,udp". */
        val network: String = "tcp,udp",
        val clients: List<ShadowsocksClient> = emptyList(),
    ) : ProtocolSettings {
        override val protocolName: String = "shadowsocks"
    }

    data class Hysteria(
        val version: Int = 2,
        val clients: List<HysteriaClient>,
    ) : ProtocolSettings {
        override val protocolName: String = "hysteria"
    }

    data class Tun(
        val mtu: Int = 1500,
        val gso: Boolean = false,
        val gro: Boolean = false,
        val enableExFilter: Boolean = false,
        val strictRoute: Boolean = true,
        val routeAddress: List<String> = emptyList(),
        val routeAddressSet: List<String> = emptyList(),
        val routeExcludeAddress: List<String> = emptyList(),
        val routeExcludeAddressSet: List<String> = emptyList(),
    ) : ProtocolSettings {
        override val protocolName: String = "tun"
    }

    data class Socks(
        /** "noauth" or "password". */
        val auth: String = "password",
        val accounts: List<UserPass> = emptyList(),
        val udp: Boolean = false,
        val ip: String = "127.0.0.1",
    ) : ProtocolSettings {
        /** Panel calls this "Mixed"; wire field is `mixed`. */
        override val protocolName: String = "mixed"
    }

    data class Http(
        val accounts: List<UserPass> = emptyList(),
        val allowTransparent: Boolean = false,
    ) : ProtocolSettings {
        override val protocolName: String = "http"
    }

    data class Wireguard(
        val secretKey: String,
        val mtu: Int = 1420,
        val peers: List<WgPeer> = emptyList(),
        /** Inbound listen address inside the WG tunnel (e.g. "10.0.0.1/32"). */
        val noKernelTun: Boolean = false,
    ) : ProtocolSettings {
        override val protocolName: String = "wireguard"
    }

    data class Dokodemo(
        val address: String,
        val targetPort: Int,
        val network: String = "tcp,udp",
        val followRedirect: Boolean = false,
    ) : ProtocolSettings {
        /** Panel calls this "Tunnel"; wire field is `tunnel`. */
        override val protocolName: String = "tunnel"
    }
}

// ----- Client structs (one per protocol family that has clients) -----

data class VlessClient(
    val id: String,
    val email: String,
    val flow: String = "",
    val totalGB: Long = 0L,
    val expiryTime: Long = 0L,
    val limitIp: Int = 0,
    val subId: String = "",
    val tgId: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val enable: Boolean = true,
)

data class VmessClient(
    val id: String,
    val email: String,
    val totalGB: Long = 0L,
    val expiryTime: Long = 0L,
    val limitIp: Int = 0,
    val subId: String = "",
    val tgId: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val enable: Boolean = true,
)

data class TrojanClient(
    val password: String,
    val email: String,
    val flow: String = "",
    val totalGB: Long = 0L,
    val expiryTime: Long = 0L,
    val limitIp: Int = 0,
    val subId: String = "",
    val tgId: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val enable: Boolean = true,
)

data class ShadowsocksClient(
    val password: String,
    val method: String = "",
    val email: String,
    val totalGB: Long = 0L,
    val expiryTime: Long = 0L,
    val limitIp: Int = 0,
    val subId: String = "",
    val tgId: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val enable: Boolean = true,
)

data class HysteriaClient(
    val auth: String,
    val email: String,
    val totalGB: Long = 0L,
    val expiryTime: Long = 0L,
    val limitIp: Int = 0,
    val subId: String = "",
    val tgId: String = "",
    val comment: String = "",
    val reset: Int = 0,
    val enable: Boolean = true,
)

data class UserPass(val user: String, val pass: String)

data class WgPeer(
    val publicKey: String,
    val allowedIPs: List<String> = listOf("0.0.0.0/0", "::/0"),
    val presharedKey: String = "",
    val keepAlive: Int = 0,
)

data class Fallback(
    val alpn: String = "",
    val name: String = "",
    val path: String = "",
    val dest: String,
    val xver: Int = 0,
)

// ----- Stream / transport / security -----

data class StreamConfig(
    val transport: TransportConfig,
    val security: SecurityConfig,
)

sealed interface TransportConfig {
    val network: String

    data class Tcp(val header: TcpHeader = TcpHeader.None) : TransportConfig {
        override val network: String = "tcp"
    }

    data class Ws(
        val path: String = "/",
        val host: String = "",
        val headers: Map<String, String> = emptyMap(),
    ) : TransportConfig {
        override val network: String = "ws"
    }

    data class Grpc(
        val serviceName: String = "",
        val authority: String = "",
        val multiMode: Boolean = false,
    ) : TransportConfig {
        override val network: String = "grpc"
    }

    data class HttpUpgrade(
        val path: String = "/",
        val host: String = "",
    ) : TransportConfig {
        override val network: String = "httpupgrade"
    }

    data class XHttp(
        val path: String = "/",
        val host: String = "",
        /** "auto" | "packet-up" | "stream-up" | "stream-one". */
        val mode: String = "auto",
    ) : TransportConfig {
        override val network: String = "xhttp"
    }

    data class Kcp(
        val mtu: Int = 1350,
        val tti: Int = 50,
        val uplinkCapacity: Int = 5,
        val downlinkCapacity: Int = 20,
        val congestion: Boolean = false,
        val readBufferSize: Int = 2,
        val writeBufferSize: Int = 2,
        val seed: String = "",
        val header: KcpHeader = KcpHeader.None,
    ) : TransportConfig {
        override val network: String = "kcp"
    }

    data class HysteriaTransport(
        val auth: String = "",
        val udpIdleTimeout: Int = 60,
    ) : TransportConfig {
        override val network: String = "hysteria"
    }
}

sealed interface TcpHeader {
    data object None : TcpHeader
    /** HTTP obfuscation; [path] and [host] are populated into header.request. */
    data class Http(val path: String = "/", val host: String = "") : TcpHeader
}

sealed interface KcpHeader {
    val type: String

    data object None : KcpHeader { override val type = "none" }
    data object Srtp : KcpHeader { override val type = "srtp" }
    data object Utp : KcpHeader { override val type = "utp" }
    data object WechatVideo : KcpHeader { override val type = "wechat-video" }
    data object DtlsHeader : KcpHeader { override val type = "dtls" }
    data object WireguardHeader : KcpHeader { override val type = "wireguard" }
}

sealed interface SecurityConfig {
    val type: String

    data object None : SecurityConfig { override val type = "none" }

    data class Tls(
        val serverName: String = "",
        val minVersion: String = "1.2",
        val maxVersion: String = "1.3",
        val alpn: List<String> = emptyList(),
        val certificates: List<TlsCertificate> = emptyList(),
        val fingerprint: String = "",
    ) : SecurityConfig {
        override val type = "tls"
    }

    data class Reality(
        val show: Boolean = false,
        val xver: Int = 0,
        /** Format `host:port`, e.g. `yahoo.com:443`. */
        val dest: String,
        val serverNames: List<String>,
        val privateKey: String,
        val publicKey: String,
        val shortIds: List<String>,
        val minClient: String = "",
        val maxClient: String = "",
        val maxTimediff: Int = 0,
        val fingerprint: String = "chrome",
    ) : SecurityConfig {
        override val type = "reality"
    }
}

data class TlsCertificate(
    val certificateFile: String = "",
    val keyFile: String = "",
    val certificate: List<String> = emptyList(),
    val key: List<String> = emptyList(),
)

// ----- Sniffing -----

data class SniffingConfig(
    val enabled: Boolean,
    val destOverride: List<String>,
    val metadataOnly: Boolean,
    val routeOnly: Boolean,
) {
    companion object {
        val Default = SniffingConfig(
            enabled = true,
            destOverride = listOf("http", "tls", "quic"),
            metadataOnly = false,
            routeOnly = false,
        )
        val Disabled = SniffingConfig(
            enabled = false,
            destOverride = emptyList(),
            metadataOnly = false,
            routeOnly = false,
        )
    }
}
