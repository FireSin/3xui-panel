package com.firesin.xuipanel.core.common

/**
 * SPI for persisting a captured SPKI pin after lazy-TOFU capture.
 * Implemented by the data layer; injected into the network layer to avoid
 * a `:core:network` → `:core:data` dependency cycle.
 */
interface PanelPinWriter {
    suspend fun writePin(panelId: String, spkiBase64: String)
}
