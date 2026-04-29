package com.firesin.xuipanel.core.network.tls

import java.security.cert.CertificateException

/** Thrown by [PinningTrustManager] when the observed leaf SPKI does not match the pinned value. */
class SpkiPinMismatchException(
    val observedSpki: String,
) : CertificateException(MESSAGE) {

    companion object {
        const val MESSAGE = "PIN_MISMATCH"
    }
}
