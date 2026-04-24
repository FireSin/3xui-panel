package com.firesin.xuipanel.core.xui

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class XuiSession(
    val panelId: String,
    val issuedAt: Long = System.currentTimeMillis(),
) {
    fun isExpired(): Boolean =
        System.currentTimeMillis() - issuedAt > SESSION_TTL_MS

    private companion object {
        // Proactively expire after 50 min; panels default to 60 min.
        const val SESSION_TTL_MS = 50L * 60 * 1_000
    }
}

@Singleton
class XuiSessionCache @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, XuiSession>()

    fun get(panelId: String): XuiSession? {
        val session = sessions[panelId] ?: return null
        if (session.isExpired()) {
            sessions.remove(panelId)
            return null
        }
        return session
    }

    fun put(panelId: String) {
        sessions[panelId] = XuiSession(panelId)
    }

    fun invalidate(panelId: String) {
        sessions.remove(panelId)
    }
}
