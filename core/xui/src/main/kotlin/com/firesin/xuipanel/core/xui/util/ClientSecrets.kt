package com.firesin.xuipanel.core.xui.util

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private val secureRandom = SecureRandom()

private val LOWER_AND_DIGITS = ('a'..'z') + ('0'..'9')

/** Returns a random UUID string (e.g. `"e927a2d3-0bbd-4f8b-9493-b65eab8bfbe3"`). */
fun randomUuid(): String = UUID.randomUUID().toString()

/**
 * Returns a 32-byte random password encoded as standard Base64 (RFC 4648, with padding).
 * Matches upstream `randomShadowSocksPassword` which uses Go's `base64.StdEncoding`.
 */
fun randomShadowsocksPassword(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

/** Returns a 16-character random string from `[a-z0-9]`. */
fun randomSubId(): String {
    val alphabet = LOWER_AND_DIGITS
    return buildString(16) {
        repeat(16) { append(alphabet[secureRandom.nextInt(alphabet.size)]) }
    }
}

/** Returns a 32-character alphanumeric [a-z0-9] string suitable as a Trojan password. */
fun randomTrojanPassword(): String {
    val alphabet = LOWER_AND_DIGITS
    return buildString(32) {
        repeat(32) { append(alphabet[secureRandom.nextInt(alphabet.size)]) }
    }
}

/** Returns a 32-character alphanumeric [a-z0-9] string suitable as a Hysteria auth secret. */
fun randomHysteriaAuth(): String {
    val alphabet = LOWER_AND_DIGITS
    return buildString(32) {
        repeat(32) { append(alphabet[secureRandom.nextInt(alphabet.size)]) }
    }
}
