package com.firesin.xuipanel.core.network.tls

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.X509TrustManager

/**
 * Trust manager used during probe and lazy-capture flows.
 *
 * First handshake: captures the leaf SPKI into [capturedSpki] atomically (compareAndSet).
 * Subsequent concurrent handshakes: if the leaf SPKI matches the captured value, accept;
 * otherwise throw [SpkiPinMismatchException] — even if system trust would pass it.
 * This prevents a MITM from slipping a different cert through during the TOFU window.
 *
 * System [delegate] validation is attempted but failures are swallowed — TOFU explicitly
 * accepts self-signed certificates.
 *
 * Used only via OkHttp; non-OkHttp consumers must wrap in X509ExtendedTrustManager for SAN validation.
 */
internal class CapturingTrustManager(
    private val delegate: X509TrustManager,
) : X509TrustManager {

    val capturedSpki: AtomicReference<String?> = AtomicReference(null)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
        val spki = SpkiHasher.sha256Base64(leaf.publicKey.encoded)
        if (!capturedSpki.compareAndSet(null, spki)) {
            // Already captured by a concurrent handshake — must match.
            val first = capturedSpki.get()!!
            if (spki != first) throw SpkiPinMismatchException(spki)
        }
        // Try system validation but swallow failure — TOFU accepts self-signed.
        try {
            delegate.checkServerTrusted(chain, authType)
        } catch (_: CertificateException) {
            // Accept anyway — self-signed is expected here.
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
