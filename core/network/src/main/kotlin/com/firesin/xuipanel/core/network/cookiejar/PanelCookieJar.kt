package com.firesin.xuipanel.core.network.cookiejar

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory per-panel cookie store.
 * Does not persist across process restarts (accepted trade-off for MVP).
 */
class PanelCookieJar : CookieJar {

    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(store) {
            // Replace cookies with the same name.
            cookies.forEach { incoming ->
                store.removeAll { it.name == incoming.name }
                store.add(incoming)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return synchronized(store) {
            store.filter { it.matches(url) }.toList()
        }
    }

    fun clear() {
        synchronized(store) { store.clear() }
    }
}
