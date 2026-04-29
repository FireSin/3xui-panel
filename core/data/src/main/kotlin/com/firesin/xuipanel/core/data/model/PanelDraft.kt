package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.TlsMode

data class PanelDraft(
    val name: String,
    val baseUrl: String,
    val login: String,
    val password: String,
    val tlsMode: TlsMode,
)
