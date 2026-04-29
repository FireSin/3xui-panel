package com.firesin.xuipanel.core.data.model

import com.firesin.xuipanel.core.common.PanelTls

/** Converts the panel's TLS configuration to a [PanelTls] value for OkHttp/XuiClient. */
fun Panel.toPanelTls() = PanelTls(
    mode = tlsMode,
    pinnedSpkiSha256 = pinnedSpkiSha256,
)
