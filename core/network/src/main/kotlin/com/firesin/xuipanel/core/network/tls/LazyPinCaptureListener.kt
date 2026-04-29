package com.firesin.xuipanel.core.network.tls

import android.util.Log
import com.firesin.xuipanel.core.common.PanelPinWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp [EventListener] that captures the leaf certificate SPKI on a TLS handshake
 * and persists it via [panelPinWriter].
 *
 * Uses [secureConnectEnd] instead of [connectionAcquired] so it fires only on an actual
 * handshake, never on a reused connection (which would have no new certificate chain).
 *
 * [captureGuard] is a shared [AtomicBoolean] passed from the factory — one per cached
 * client, shared across all [EventListener] instances created by the factory. This ensures
 * exactly one write+invalidate even when multiple calls race during the TOFU window.
 * TM-level dedup (Fix 1 / [CapturingTrustManager]) guarantees only one SPKI reaches here;
 * the guard prevents a redundant DB write on the same panelId.
 *
 * The write and invalidate are dispatched on [scope] (Dispatchers.IO) — the OkHttp
 * dispatcher thread is not blocked.
 */
internal class LazyPinCaptureListener(
    private val panelId: String,
    private val panelPinWriter: PanelPinWriter,
    private val onPinCaptured: (String) -> Unit,
    private val captureGuard: AtomicBoolean,
    private val scope: CoroutineScope,
) : EventListener() {

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        if (!captureGuard.compareAndSet(false, true)) return

        val leaf = handshake?.peerCertificates?.firstOrNull() ?: run {
            Log.w("LazyPinCapture", "Empty peer certificate chain for panel $panelId; will retry")
            captureGuard.set(false) // allow retry on next handshake
            return
        }

        val spki = SpkiHasher.sha256Base64(leaf.publicKey.encoded)
        scope.launch {
            panelPinWriter.writePin(panelId, spki)
            onPinCaptured(panelId)
        }
    }
}
