package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.TlsMode

data class PanelDraft(
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val tlsMode: TlsMode,
    val apiToken: String? = null,
    /** OTP entered by the user. Null/blank = no 2FA code. Ignored when [apiToken] is set. */
    val twoFactorCode: String? = null,
    /** Persisted after a successful 2FA probe so future error messages can be more specific. */
    val twoFactorEnabled: Boolean = false,
)
