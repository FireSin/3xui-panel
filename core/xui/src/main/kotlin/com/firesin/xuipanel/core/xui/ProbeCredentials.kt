package com.firesin.xuipanel.core.xui

import com.firesin.xuipanel.core.common.TlsMode

data class ProbeCredentials(
    val baseUrl: String,
    val login: String,
    val password: String,
    val tlsMode: TlsMode,
    /** Required when [tlsMode] is [TlsMode.PINNED] and a pin is already stored. */
    val pinnedSpkiSha256: String? = null,
    /** When non-blank, probe uses Bearer auth instead of form login. */
    val apiToken: String? = null,
    /** OTP code for 2FA-enabled panels. Ignored when [apiToken] is set. */
    val twoFactorCode: String? = null,
)

/** Outcome of a successful [XuiClient.probeLogin]. */
data class ProbeOutcome(
    /** SPKI SHA-256 base64 of the leaf certificate observed during this probe. Null if TLS was bypassed or not applicable. */
    val capturedSpkiBase64: String?,
)
