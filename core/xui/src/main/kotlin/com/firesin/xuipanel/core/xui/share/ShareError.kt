package com.firesin.xuipanel.core.xui.share

/**
 * Local error type for URI generation. Does not extend [com.firesin.xuipanel.core.common.DomainError]
 * because no network I/O is involved.
 *
 * Design: docs/architecture/share-config.md §7
 */
sealed class ShareError {
    /** The inbound's transport network (e.g. "kcp", "http") is not supported for URI generation. */
    data class UnsupportedTransport(val network: String) : ShareError()

    /** The raw streamSettings JSON is malformed or contains unexpected structure. */
    data class InvalidStreamSettings(val reason: String) : ShareError()

    /**
     * The Shadowsocks inbound uses a 2022-series cipher but the inbound-level
     * password is absent from [InboundDto.settings].
     */
    data object MissingInboundPassword : ShareError()

    /** The client protocol is not supported for URI generation. */
    data class UnsupportedProtocol(val protocol: String) : ShareError()
}
