package com.firesin.xuipanel.core.network.tls

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * No-op TrustManager that accepts any certificate.
 * Used only when the user explicitly enables "trust self-signed" for a panel.
 * A warning is shown in the UI whenever this mode is active.
 */
internal class NoOpTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
