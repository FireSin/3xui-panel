package com.firesin.xuipanel.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

private const val HEADER_CSRF = "X-CSRF-Token"
private const val CSRF_TOKEN_PATH = "csrf-token"

/**
 * OkHttp interceptor that injects `X-CSRF-Token` on every non-GET request.
 *
 * Flow:
 * 1. GET requests pass through unchanged.
 * 2. Requests to the csrf-token endpoint itself pass through (avoid recursion).
 * 3. For non-GET requests: read token from [store]. If absent — fetch synchronously
 *    via [tokenClient] (which does NOT have this interceptor), cache in [store].
 * 4. Add `X-CSRF-Token` header and proceed.
 * 5. On HTTP 403 response: invalidate [store], fetch fresh token, retry **once**.
 *
 * [tokenClient] must be the base OkHttpClient WITHOUT this interceptor to prevent
 * infinite recursion.
 *
 * [csrfTokenUrl] is the absolute URL of the `GET csrf-token` endpoint,
 * e.g. `https://host:port/path/csrf-token`.
 */
class CsrfInterceptor(
    private val panelId: String,
    private val csrfTokenUrl: String,
    private val store: CsrfTokenStore,
    private val tokenClient: OkHttpClient,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // GET requests and the csrf-token endpoint itself pass through untouched.
        if (request.method == "GET" || request.url.toString().contains(CSRF_TOKEN_PATH)) {
            return chain.proceed(request)
        }

        val token = store.get(panelId) ?: fetchAndStore()
        val response = chain.proceed(request.withCsrf(token))

        if (response.code == HTTP_FORBIDDEN) {
            response.close()
            store.invalidate(panelId)
            val freshToken = fetchAndStore()
            return chain.proceed(request.withCsrf(freshToken))
        }

        return response
    }

    /**
     * Synchronously fetches a CSRF token using [tokenClient] (no CSRF interceptor
     * on that client — no recursion). Caches the result in [store] and returns it.
     * Throws [IllegalStateException] if the fetch fails so the caller bubbles an
     * IOException to the standard error-mapping layer.
     */
    private fun fetchAndStore(): String {
        val req = Request.Builder().url(csrfTokenUrl).get().build()
        val body = tokenClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("csrf-token fetch failed: HTTP ${resp.code}")
            resp.body?.string() ?: error("csrf-token response is empty")
        }
        // Response shape: {"success":true,"msg":"","obj":"<token>"}
        val token = runCatching { JSONObject(body).optString("obj", "") }.getOrElse { "" }
        if (token.isBlank()) error("csrf-token value is blank for panel $panelId")
        store.put(panelId, token)
        return token
    }

    private fun Request.withCsrf(token: String): Request =
        newBuilder().header(HEADER_CSRF, token).build()

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
