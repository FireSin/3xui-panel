package com.firesin.xuipanel.core.common

/**
 * Lightweight, single-purpose lookup for panel metadata that XuiClient needs
 * without taking a dependency on :core:data (which depends on :core:xui).
 * Implementation lives in :core:data and is bound via Hilt.
 */
interface PanelLookup {
    suspend fun lookup(panelId: String): PanelMetadata?
}

data class PanelMetadata(
    val name: String,
    val twoFactorEnabled: Boolean,
)
