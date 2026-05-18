package com.firesin.xuipanel.core.network

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory per-panel CSRF token cache.
 *
 * Tokens are minted by the panel's gorilla/securecookie layer (~24h TTL). We don't
 * track TTL — instead we invalidate on HTTP 403, which triggers a fresh fetch.
 */
@Singleton
class CsrfTokenStore @Inject constructor() {

    private val tokens = ConcurrentHashMap<String, String>()

    fun get(panelId: String): String? = tokens[panelId]

    fun put(panelId: String, token: String) {
        tokens[panelId] = token
    }

    fun invalidate(panelId: String) {
        tokens.remove(panelId)
    }
}
