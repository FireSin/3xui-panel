package com.firesin.xuipanel.core.common

/**
 * Identifies the TLS mode of a panel.
 *
 * [SYSTEM] — use the device trust store; no SPKI pinning.
 * [PINNED] — accept only the leaf certificate whose SPKI SHA-256 matches [pinnedSpkiSha256].
 *            When [pinnedSpkiSha256] is null the pin has not been captured yet (migrated panel);
 *            the next successful connection will capture and persist it.
 */
enum class TlsMode { SYSTEM, PINNED }

data class PanelTls(
    val mode: TlsMode,
    val pinnedSpkiSha256: String?,
)
