package com.firesin.xuipanel.core.network

import com.firesin.xuipanel.core.network.cookiejar.PanelCookieJar
import com.firesin.xuipanel.core.network.tls.NoOpTrustManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext

private data class ClientKey(val panelId: String, val trustSelfSigned: Boolean)

/**
 * Caches one [OkHttpClient] per panel. Re-creates on trust-flag change.
 * Each client has its own [PanelCookieJar] for isolated session management.
 */
@Singleton
class OkHttpClientFactory @Inject constructor(
    private val loggingInterceptor: HttpLoggingInterceptor,
) {

    private val cache = ConcurrentHashMap<ClientKey, Pair<OkHttpClient, PanelCookieJar>>()

    fun getClient(panelId: String, trustSelfSigned: Boolean): OkHttpClient {
        val key = ClientKey(panelId, trustSelfSigned)
        return cache.getOrPut(key) { buildEntry(trustSelfSigned) }.first
    }

    fun getCookieJar(panelId: String, trustSelfSigned: Boolean): PanelCookieJar {
        val key = ClientKey(panelId, trustSelfSigned)
        return cache.getOrPut(key) { buildEntry(trustSelfSigned) }.second
    }

    /** Invalidates the cached client — call when panel URL or trust-flag changes. */
    fun invalidate(panelId: String) {
        cache.keys.filter { it.panelId == panelId }.forEach { cache.remove(it) }
    }

    private fun buildEntry(trustSelfSigned: Boolean): Pair<OkHttpClient, PanelCookieJar> {
        val cookieJar = PanelCookieJar()
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

        if (trustSelfSigned) {
            val trustManager = NoOpTrustManager()
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), null)
            }
            builder
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
        }

        return builder.build() to cookieJar
    }

    private companion object {
        const val CONNECT_TIMEOUT_SEC = 10L
        const val READ_TIMEOUT_SEC = 20L
    }
}
