package com.firesin.xuipanel.core.xui

/**
 * Thrown when the panel rejects credentials even after a re-login attempt.
 */
class XuiAuthException(panelId: String) : Exception("Authentication failed for panel $panelId")
