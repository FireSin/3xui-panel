package com.firesin.xuipanel.core.network

import com.firesin.xuipanel.core.common.PanelPinWriter
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.network.cookiejar.PanelCookieJar
import com.firesin.xuipanel.core.network.tls.CapturingTrustManager
import com.firesin.xuipanel.core.network.tls.LazyPinCaptureListener
import com.firesin.xuipanel.core.network.tls.PinningTrustManager
import com.firesin.xuipanel.core.network.tls.ProbePinCaptureListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Identifies a base (no-CSRF) client by panel identity. */
private data class BaseKey(val panelId: String, val tls: PanelTls)

/** Identifies a CSRF-aware client additionally by the base URL used to mint the token. */
private data class CsrfKey(val panelId: String, val tls: PanelTls, val csrfBaseUrl: String)

/**
 * Caches one [OkHttpClient] per panel. Re-creates on TLS-configuration change.
 * Each panel has a single [PanelCookieJar] shared across its base and CSRF-aware clients.
 *
 * TLS modes:
 * - [TlsMode.SYSTEM] — use the device trust store; no custom TrustManager.
 * - [TlsMode.PINNED] with a non-null pin — install [PinningTrustManager].
 * - [TlsMode.PINNED] with a null pin — install [CapturingTrustManager] (lazy TOFU);
 *   captures SPKI via [LazyPinCaptureListener] on first connection and persists via
 *   [panelPinWriter]; invalidates the entry so the next request uses [PinningTrustManager].
 */
@Singleton
class OkHttpClientFactory @Inject constructor(
    private val loggingInterceptor: HttpLoggingInterceptor,
    private val panelPinWriter: PanelPinWriter,
    private val csrfTokenStore: CsrfTokenStore,
) {

    /**
     * Base clients (no CSRF interceptor). Keyed by (panelId, tls).
     * Stores client + cookie jar. The cookie jar is shared with CSRF-aware clients.
     */
    private val baseCache = ConcurrentHashMap<BaseKey, Pair<OkHttpClient, PanelCookieJar>>()

    /**
     * CSRF-aware clients. Keyed by (panelId, tls, csrfBaseUrl).
     * The OkHttpClient wraps the base client's connection pool but adds [CsrfInterceptor].
     */
    private val csrfCache = ConcurrentHashMap<CsrfKey, OkHttpClient>()

    /**
     * Shared capture guards, keyed by panelId. Each AtomicBoolean is created with the client and
     * ensures exactly one write+invalidate across all EventListener instances for the same client.
     */
    private val captureGuards = ConcurrentHashMap<String, AtomicBoolean>()

    /** Scope for async DB writes from [LazyPinCaptureListener] — never blocks OkHttp threads. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Returns a cached [OkHttpClient] for the given panel.
     *
     * When [csrfBaseUrl] is non-null, the returned client includes a [CsrfInterceptor] that
     * automatically injects `X-CSRF-Token` on every non-GET request. The CSRF-aware client
     * shares the same [PanelCookieJar] as the base client, so session cookies written during
     * login are visible to both.
     *
     * When [csrfBaseUrl] is null, returns the base client (used for WebSocket and probe flows).
     */
    fun getClient(panelId: String, tls: PanelTls, csrfBaseUrl: String? = null): OkHttpClient {
        val baseEntry = baseCache.getOrPut(BaseKey(panelId, tls)) { buildBaseEntry(panelId, tls) }
        if (csrfBaseUrl == null) return baseEntry.first

        val csrfKey = CsrfKey(panelId, tls, csrfBaseUrl)
        return csrfCache.getOrPut(csrfKey) {
            buildCsrfClient(panelId, csrfBaseUrl, baseEntry)
        }
    }

    /** Returns the [PanelCookieJar] for the given panel. Shared by base and CSRF-aware clients. */
    fun getCookieJar(panelId: String, tls: PanelTls): PanelCookieJar {
        return baseCache.getOrPut(BaseKey(panelId, tls)) { buildBaseEntry(panelId, tls) }.second
    }

    /** Invalidates all cached clients for [panelId] — call when panel URL or TLS config changes. */
    fun invalidate(panelId: String) {
        baseCache.keys.filter { it.panelId == panelId }.forEach { baseCache.remove(it) }
        csrfCache.keys.filter { it.panelId == panelId }.forEach { csrfCache.remove(it) }
        captureGuards.remove(panelId)
    }

    /**
     * Builds a one-off client not attached to any panelId cache entry.
     * Use for probe logins before a panel is persisted.
     * Returns the client and the [ProbePinCaptureListener] if one was installed (null otherwise).
     */
    fun buildTransient(tls: PanelTls): Pair<OkHttpClient, ProbePinCaptureListener?> {
        val cookieJar = PanelCookieJar()
        return buildProbeClient(cookieJar, tls)
    }

    private fun buildBaseEntry(panelId: String, tls: PanelTls): Pair<OkHttpClient, PanelCookieJar> {
        val cookieJar = PanelCookieJar()
        val client = buildBaseClient(panelId, cookieJar, tls)
        return client to cookieJar
    }

    /**
     * Builds the base (no-CSRF) client. For the lazy-TOFU case (PINNED + null pin), attaches a
     * [LazyPinCaptureListener] that writes the pin and invalidates this entry on first
     * connection — no polling, no timeout.
     */
    private fun buildBaseClient(
        panelId: String,
        cookieJar: PanelCookieJar,
        tls: PanelTls,
    ): OkHttpClient {
        val builder = baseBuilder(cookieJar)
        applyTls(builder, tls)

        if (tls.mode == TlsMode.PINNED && tls.pinnedSpkiSha256 == null) {
            val captureGuard = captureGuards.getOrPut(panelId) { AtomicBoolean(false) }
            builder.eventListenerFactory {
                LazyPinCaptureListener(
                    panelId = panelId,
                    panelPinWriter = panelPinWriter,
                    onPinCaptured = { id -> invalidate(id) },
                    captureGuard = captureGuard,
                    scope = ioScope,
                )
            }
        }

        return builder.build()
    }

    /**
     * Builds a CSRF-aware client on top of [baseEntry].
     *
     * The new client shares the same [PanelCookieJar] (and therefore session cookies) with
     * the base client. [CsrfInterceptor] uses the base client as its token-fetch client to
     * avoid infinite recursion.
     */
    private fun buildCsrfClient(
        panelId: String,
        csrfBaseUrl: String,
        baseEntry: Pair<OkHttpClient, PanelCookieJar>,
    ): OkHttpClient {
        val (baseClient, cookieJar) = baseEntry
        val csrfTokenUrl = if (csrfBaseUrl.endsWith("/")) "${csrfBaseUrl}csrf-token"
                           else "$csrfBaseUrl/csrf-token"
        // newBuilder() inherits the connection pool, dispatcher, and cookie jar from baseClient.
        return baseClient.newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor(CsrfInterceptor(panelId, csrfTokenUrl, csrfTokenStore, baseClient))
            .build()
    }

    /**
     * Builds a transient probe client. For the TOFU case, attaches a [ProbePinCaptureListener]
     * so the probe can read the observed SPKI from [ProbePinCaptureListener.capturedSpki].
     */
    private fun buildProbeClient(
        cookieJar: PanelCookieJar,
        tls: PanelTls,
    ): Pair<OkHttpClient, ProbePinCaptureListener?> {
        val builder = baseBuilder(cookieJar)
        applyTls(builder, tls)

        var listener: ProbePinCaptureListener? = null
        if (tls.mode == TlsMode.PINNED && tls.pinnedSpkiSha256 == null) {
            listener = ProbePinCaptureListener()
            builder.eventListener(listener)
        }

        return builder.build() to listener
    }

    private fun baseBuilder(cookieJar: PanelCookieJar) = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)

    private fun applyTls(builder: OkHttpClient.Builder, tls: PanelTls) {
        when {
            tls.mode == TlsMode.PINNED && tls.pinnedSpkiSha256 != null -> {
                val pinningTm = PinningTrustManager(systemTrustManager(), tls.pinnedSpkiSha256!!)
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(pinningTm), null)
                }
                builder.sslSocketFactory(sslContext.socketFactory, pinningTm)
            }

            tls.mode == TlsMode.PINNED && tls.pinnedSpkiSha256 == null -> {
                // Lazy capture — CapturingTrustManager accepts the cert; EventListener reads the SPKI.
                val tm = CapturingTrustManager(systemTrustManager())
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(tm), null)
                }
                builder.sslSocketFactory(sslContext.socketFactory, tm)
            }

            else -> {
                // SYSTEM — default trust, no custom TrustManager.
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SEC = 10L
        const val READ_TIMEOUT_SEC = 20L

        fun systemTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as java.security.KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}
