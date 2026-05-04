package com.firesin.xuipanel.core.xui.dto

/**
 * Parsed projection of Xray's StreamConfig blob stored in [InboundDto.streamSettings].
 *
 * Only the fields needed for URI generation are retained.
 * Unknown fields are silently ignored at parse time (see [StreamSettingsParser]).
 *
 * Design: docs/architecture/share-config.md §3
 */
sealed interface StreamSettings {

    /** TLS / Reality / none security layer attached to the transport. */
    val security: Security

    /**
     * TCP transport.
     *
     * [headerType] is `"http"` when the inbound uses HTTP obfuscation headers;
     * absent (null) otherwise.  When `"http"`, [path] and [host] are derived from
     * `tcpSettings.header.request`.
     *
     * upstream: sub/subService.go applyShareNetworkParams case "tcp" @main
     */
    data class Tcp(
        val headerType: String?,
        val path: String?,
        val host: String?,
        override val security: Security,
    ) : StreamSettings

    /**
     * WebSocket transport.
     *
     * [path] from `wsSettings.path`; [host] from `wsSettings.host` or
     * `wsSettings.headers.Host`.
     *
     * upstream: sub/subService.go applyPathAndHostParams for "ws" @main
     */
    data class Ws(
        val path: String?,
        val host: String?,
        override val security: Security,
    ) : StreamSettings

    /**
     * gRPC transport.
     *
     * upstream: sub/subService.go applyShareNetworkParams case "grpc" @main
     */
    data class Grpc(
        val serviceName: String,
        val authority: String?,
        val multiMode: Boolean,
        override val security: Security,
    ) : StreamSettings

    /**
     * HTTP Upgrade transport.
     *
     * upstream: sub/subService.go applyPathAndHostParams for "httpupgrade" @main
     */
    data class HttpUpgrade(
        val path: String?,
        val host: String?,
        override val security: Security,
    ) : StreamSettings

    /**
     * XHTTP transport (also known as SplitHTTP).
     *
     * upstream: sub/subService.go applyShareNetworkParams case "xhttp" @main
     */
    data class XHttp(
        val path: String?,
        val host: String?,
        val mode: String?,
        override val security: Security,
    ) : StreamSettings

    /**
     * KCP or legacy HTTP/2 ("http") transport — not supported for URI generation.
     *
     * Parser emits this variant rather than returning an error so that callers can
     * inspect [network] for display / logging before deciding to surface
     * [com.firesin.xuipanel.core.xui.share.ShareError.UnsupportedTransport].
     *
     * upstream: kcp present in subService.go but not emittable via standard
     * VLESS/VMESS links in modern configurations @main
     */
    data class Unsupported(val network: String, override val security: Security) : StreamSettings
}

/**
 * Security layer for a stream.
 *
 * Design: docs/architecture/share-config.md §3
 */
sealed interface Security {

    /** No TLS — `security = "none"` or field absent. */
    data object None : Security

    /**
     * TLS security layer.
     *
     * [sni] from `tlsSettings.serverName`; [alpn] from `tlsSettings.alpn[]`;
     * [fingerprint] from `tlsSettings.settings.fingerprint`.
     *
     * upstream: sub/subService.go applyShareTLSParams @main
     */
    data class Tls(
        val sni: String?,
        val alpn: List<String>,
        val fingerprint: String?,
    ) : Security

    /**
     * Reality security layer.
     *
     * Only the **public** fields needed to build a share link are stored here.
     * `privateKey`, `mldsa65Seed`, and `mldsa65Verify` are never parsed — they
     * are dropped silently by the parser (`ignoreUnknownKeys = true`).
     *
     * [sni]  — `realitySettings.serverNames[0]` (index 0; upstream picks random).
     * [pbk]  — `realitySettings.settings.publicKey`.
     * [sid]  — `realitySettings.shortIds[0]` (index 0; upstream picks random).
     * [fp]   — `realitySettings.settings.fingerprint`.
     *
     * upstream: sub/subService.go applyShareRealityParams @main
     */
    data class Reality(
        val sni: String?,
        val pbk: String?,
        val sid: String?,
        val fp: String?,
    ) : Security
}
