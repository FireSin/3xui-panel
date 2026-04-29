package com.firesin.xuipanel.core.network.tls

import kotlinx.coroutines.CompletableDeferred
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener

/**
 * OkHttp [EventListener] for the transient probe path.
 *
 * Captures the leaf SPKI on [connectionAcquired] and completes [capturedSpki].
 * If a second distinct SPKI is observed on the same probe (e.g., redirect to a different
 * host), the deferred is completed exceptionally with [SpkiPinMismatchException] — the
 * probe will surface this as [com.firesin.xuipanel.core.common.DomainError.PinMismatch].
 */
internal class ProbePinCaptureListener : EventListener() {

    /** Completed with the captured SPKI string, or exceptionally on conflicting SPKIs. */
    val capturedSpki: CompletableDeferred<String?> = CompletableDeferred()

    private var firstSpki: String? = null

    override fun connectionAcquired(call: Call, connection: Connection) {
        val leaf = connection.handshake()?.peerCertificates?.firstOrNull()
        if (leaf == null) {
            capturedSpki.complete(null)
            return
        }
        val spki = SpkiHasher.sha256Base64(leaf.publicKey.encoded)
        if (firstSpki == null) {
            firstSpki = spki
            capturedSpki.complete(spki)
        } else if (firstSpki != spki) {
            // Redirect to a different host with a different cert — fail loud.
            capturedSpki.completeExceptionally(SpkiPinMismatchException(spki))
        }
        // Same SPKI on a second connection — benign, no-op.
    }

    /** Returns null if no connection was acquired (e.g., cached connection). */
    fun getOrNull(): String? = if (capturedSpki.isCompleted) {
        runCatching { capturedSpki.getCompleted() }.getOrNull()
    } else {
        null
    }
}
