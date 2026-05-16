package com.firesin.xuipanel.core.common.twofactor

import kotlinx.coroutines.flow.SharedFlow

data class TwoFactorOtpRequest(
    val requestId: String,
    val panelId: String,
    val panelName: String,
)

interface TwoFactorOtpBus {
    /** Hot stream of pending OTP requests — single subscriber (MainActivity overlay). */
    val requests: SharedFlow<TwoFactorOtpRequest>

    /**
     * Suspends until UI returns OTP (or null on cancel / timeout).
     * Called by XuiClient when 401 hits a 2FA-enabled panel.
     */
    suspend fun requestOtp(panelId: String, panelName: String): String?

    /** Called by UI after user submits or cancels the dialog. */
    fun completeRequest(requestId: String, otp: String?)
}
