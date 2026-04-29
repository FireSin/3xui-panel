package com.firesin.xuipanel.core.network.tls

import java.security.MessageDigest
import java.util.Base64

internal object SpkiHasher {
    /** Computes SHA-256 of [publicKeyEncoded] and encodes the result as RFC 4648 base64 (no wrap). */
    fun sha256Base64(publicKeyEncoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
        return Base64.getEncoder().encodeToString(digest)
    }
}
