package com.firesin.xuipanel.core.network.tls

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Trust manager that accepts a server certificate chain if and only if the leaf
 * certificate's SPKI SHA-256 matches [pinnedSpki].
 *
 * Does NOT call the system trust manager for server-side checks — this allows
 * self-signed certificates that would otherwise be rejected by the system store.
 *
 * Used only via OkHttp; non-OkHttp consumers must wrap in X509ExtendedTrustManager for SAN validation.
 */
internal class PinningTrustManager(
    private val system: X509TrustManager,
    private val pinnedSpki: String,
) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
        val spki = SpkiHasher.sha256Base64(leaf.publicKey.encoded)
        if (spki != pinnedSpki) throw SpkiPinMismatchException(spki)
        // No system.checkServerTrusted — self-signed certs would fail it.
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
