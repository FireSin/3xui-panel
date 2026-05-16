package com.firesin.xuipanel.core.common

/**
 * No-op implementation of [PanelLookup] for use in unit tests.
 * Always returns null — treats every panel as non-2FA.
 */
object NoOpPanelLookup : PanelLookup {
    override suspend fun lookup(panelId: String): PanelMetadata? = null
}
